NAME=dns-made-easy-updater
LICENSE=ASL 2.0
VENDOR=Andrew Kroh
VERSION?=2.0.0
URL=https://github.com/andrewkroh/dns-made-easy-updater
DESC=Updater for DDNS records on DNS Made Easy
OUTDIR=build/packages

fmt:
	goimports -l -w *.go

build:
	go build

crosscompile:
	mkdir -p build/bin
	go get github.com/mitchellh/gox
	gox -output="build/bin/{{.Dir}}_{{.OS}}_{{.Arch}}" -os="windows,linux,solaris,darwin,freebsd,netbsd,openbsd"

docker-fpm:
	docker run --rm -v $(PWD):/repo tudorg/fpm make -C /repo packages

packages:
	GOOS=linux   GOARCH=amd64 ARCH=x86_64 TYPE=rpm $(MAKE) package
	GOOS=linux   GOARCH=386   ARCH=i686   TYPE=rpm $(MAKE) package
	GOOS=linux   GOARCH=amd64 ARCH=amd64  TYPE=deb $(MAKE) package
	GOOS=linux   GOARCH=386   ARCH=i386   TYPE=deb $(MAKE) package
	GOOS=linux   GOARCH=amd64 ARCH=amd64  TYPE=tar $(MAKE) package rename-tar
	GOOS=linux   GOARCH=386   ARCH=i386   TYPE=tar $(MAKE) package rename-tar
	GOOS=solaris GOARCH=amd64 ARCH=amd64  TYPE=tar $(MAKE) package rename-tar

package:
	mkdir -p $(OUTDIR)
	fpm --force \
		-p $(OUTDIR) \
		-s dir -t $(TYPE) \
		-v $(VERSION) \
		-n $(NAME) \
		--architecture $(ARCH) \
		--vendor "$(VENDOR)" \
		--license "$(LICENSE)" \
		--description "$(DESC)" \
		--url $(URL) \
		--config-files /etc/$(NAME).yaml \
		config.yaml=/etc/$(NAME).yaml \
		build/bin/$(NAME)_$(GOOS)_$(GOARCH)=/usr/bin/$(NAME)

rename-tar:
	mv $(OUTDIR)/$(NAME).tar $(OUTDIR)/$(NAME)-$(VERSION)-$(GOOS)-$(GOARCH).tar

clean:
	@rm -rf build $(NAME) $(NAME).exe
