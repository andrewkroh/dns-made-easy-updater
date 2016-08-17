package main

import (
	"crypto/tls"
	"flag"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"time"

	log "github.com/Sirupsen/logrus"
	"github.com/joeshaw/multierror"
	"gopkg.in/yaml.v2"
)

const (
	ipURL     = "http://myip.dnsmadeeasy.com/"
	updateURL = "https://cp.dnsmadeeasy.com/servlet/updateip?"
)

var (
	interval = flag.String("interval", "", "Interval at which to check to "+
		"IP address changes. If not given then one check (and possibly an "+
		"update) is performed then it exits.")
	username      = flag.String("username", "", "Username for updating the DNS record.")
	password      = flag.String("password", "", "Password for updating the DNS record.")
	recordID      = flag.String("record-id", "", "ID number of DDNS record.")
	currentIPFile = flag.String("current-ip-file", "", "File containing the current "+
		"public IP address. It will be created if it does not exist.")
	configFile = flag.String("config", "", "YAML config file containing username, "+
		"password, record-id, and current-ip-file.")
	verbose  = flag.Bool("v", false, "Enable verbose output to stderr")
	insecure = flag.Bool("insecure", false, "allow insecure SSL connections")
)

func init() {
	flag.Parse()
	log.SetOutput(os.Stderr)
	log.SetLevel(log.WarnLevel)
	log.SetFormatter(&log.TextFormatter{FullTimestamp: true})
	if *verbose {
		log.SetLevel(log.DebugLevel)
	}
}

type Config struct {
	Interval      time.Duration `yaml:"interval"`
	Username      string        `yaml:"username"`
	Password      string        `yaml:"password"`
	RecordID      string        `yaml:"record-id"`
	CurrentIPFile string        `yaml:"current-ip-file"`
}

func (c Config) validate() error {
	var errs multierror.Errors
	if c.Username == "" {
		errs = append(errs, fmt.Errorf("username is required"))
	}
	if c.Password == "" {
		errs = append(errs, fmt.Errorf("password is required"))
	}
	if c.RecordID == "" {
		errs = append(errs, fmt.Errorf("record-id is required"))
	}
	if c.CurrentIPFile == "" {
		errs = append(errs, fmt.Errorf("current-ip-file is required"))
	}
	return errs.Err()
}

// parseConfig interprets the command line flags are returns the config.
func parseConfig() (Config, error) {
	// Read YAML file.
	c := Config{}
	if *configFile != "" {
		content, err := ioutil.ReadFile(*configFile)
		if err != nil {
			return c, err
		}

		err = yaml.Unmarshal(content, &c)
		if err != nil {
			return c, err
		}
	}

	// Read CLI flags and override config file options.
	if *interval != "" {
		var err error
		c.Interval, err = time.ParseDuration(*interval)
		if err != nil {
			return c, fmt.Errorf("invalid interval '%s'. %v", *interval, err)
		}
	}

	// Validate the interval.
	if c.Interval != 0 && c.Interval < 10*time.Second {
		log.Fatalf("interval (%s) must be >= 10s", c.Interval)
	}

	if *username != "" {
		c.Username = *username
	}

	if *password != "" {
		c.Password = *password
	}

	if *recordID != "" {
		c.RecordID = *recordID
	}

	if *currentIPFile != "" {
		c.CurrentIPFile = *currentIPFile
	}

	return c, c.validate()
}

func getSavedIP(currentIPFile string) (net.IP, error) {
	log.Printf("Loading saved IP from %s", currentIPFile)
	content, err := ioutil.ReadFile(currentIPFile)
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}

	return net.ParseIP(strings.TrimSpace(string(content))), nil
}

func saveIP(c Config, ip net.IP) error {
	log.Printf("Saving IP %s to %s", ip.String(), c.CurrentIPFile)
	return ioutil.WriteFile(c.CurrentIPFile, []byte(ip.String()+"\n"), os.FileMode(0600))
}

func getPublicIP(client *http.Client) (net.IP, error) {
	log.Printf("Requesting public IP from %s", ipURL)
	resp, err := client.Get(ipURL)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	return net.ParseIP(strings.TrimSpace(string(body))), err
}

func updateDDNS(client *http.Client, c Config, ip net.IP) error {
	log.Printf("Updating DDNS record at %s", updateURL)
	updateQuery := fmt.Sprintf("%s?username=%s&password=%s&id=%s&ip=%s",
		updateURL, c.Username, c.Password, c.RecordID, ip.String())
	resp, err := client.Get(updateQuery)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return err
	}

	response := string(body)
	if response == "success" {
		return nil
	}

	return fmt.Errorf("failed to update DDNS record: %s", response)
}

func run(c Config) error {
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: *insecure},
	}
	client := &http.Client{Transport: tr}

	ip, err := getPublicIP(client)
	if err != nil {
		return err
	}
	log.Printf("Public IP %s", ip)

	savedIP, err := getSavedIP(c.CurrentIPFile)
	if err != nil {
		return err
	}
	log.Printf("Saved IP %s", savedIP)

	if !ip.Equal(savedIP) {
		err = updateDDNS(client, c, ip)
		if err != nil {
			return err
		}

		log.Printf("Updated record %s successfully", c.RecordID)

		err = saveIP(c, ip)
		if err != nil {
			return fmt.Errorf("failed to save IP to disk. %v", err)
		}
		log.Printf("IP %s saved to %s", ip, c.CurrentIPFile)
		return nil
	}

	log.Println("IP address is up-to-date. No changes made.")
	return nil
}

func main() {
	// Read config.
	config, err := parseConfig()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	log.Printf("Config %+v", config)

	// Determine if it runs once or continuously at an interval.
	done := make(chan struct{})
	var tick <-chan time.Time
	if config.Interval > 0 {
		ticker := time.NewTicker(config.Interval)
		tick = ticker.C
	} else {
		close(done)
	}

	// Shutdown gracefully when a signal is received. Wait for run to complete.
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	go func() {
		once := sync.Once{}
		for _ = range c {
			once.Do(func() {
				close(done)
			})
		}
	}()

	// Main loop.
	for {
		err := run(config)
		if err != nil {
			log.Fatal(err)
		}

		select {
		case <-tick:
		case <-done:
			return
		}
	}
}
