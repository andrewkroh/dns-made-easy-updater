/*
 * Copyright 2011 Andrew Kroh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krohinc.dns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * Dynamic DNS (DDNS) updater utility for DNS Made Easy. Grabs your public IP
 * address using a reflector hosted by DNS Made Easy then sends the record
 * update.
 * 
 * <p>
 * See <a target="_blank"
 * href="http://oldcp.dnsmadeeasy.com/enterprisedns/ddnstechspec.html">
 * http://oldcp.dnsmadeeasy.com/enterprisedns/ddnstechspec.html</a> for information
 * on the communication specification.
 * 
 * <p>
 * Proxy Users: If you need to use this utility behind a proxy server you can
 * set the HTTP and HTTPS proxy servers using standard Java command line
 * properties. See <a target="_blank" href=
 * "http://download.oracle.com/javase/6/docs/technotes/guides/net/proxies.html">
 * http://download.oracle.com/javase/6/docs/technotes/guides/net/proxies.html</a>
 * for information on how to configure your proxies.
 * 
 * <p>
 * 
 * @author Original Author: Andrew Kroh
 */
public class DnsMadeEasyManualUpdater
{
	/**
	 * Retrieves the public IP as seen in the HTTP request to DNS Made Easy.
	 * 
	 * @return IP address as a string
	 * @throws IOException
	 *             if there is a problem making the request
	 */
    public static String getPublicIp() throws IOException
    {
        return httpGet("http://www.dnsmadeeasy.com/myip.jsp");
    }

	/**
	 * Updates a DNS Made Easy DDNS record using a secure connection. Below are
	 * the possible return codes.
	 * 
	 * <ul>
	 * <li>"error-auth" Invalid username or password, or invalid IP syntax</li>
	 * <li>"error-auth-suspend" User has had his / her account suspended. This
	 * is if I get complaints about them or if they misuse the service.</li>
	 * <li>"error-auth-voided" User has had his / her account revoked. Same
	 * thing as suspended but this is permanent.</li>
	 * <li>"error-record-auth" User does not have access to this record.</li>
	 * <li>"error-record-ip-same" IP never changed so nothing was done.</li>
	 * <li>"error-system" General system error which is caught and recognized by
	 * the system.</li>
	 * <li>"error" General system error unrecognized by the system.</li>
	 * <li>"success" The one and only good message.</li>
	 * </ul>
	 * 
	 * @param username
	 *            user name assigned to the DDNS record (does NOT have to be the
	 *            same as your DNS Made Easy account, and probably shouldn't be
	 *            for security)
	 * @param password
	 *            password associated with the DDNS record
	 * @param recordId
	 *            record ID
	 * @param ip
	 *            IP address to update the record with
	 * 
	 * @return return code String from DNS Made Easy, either success or error
	 * 
	 * @throws IOException
	 *             if there is a problem making the update request
	 */
    public static String updateDdnsIp(String username, 
                                      String password,
                                      String recordId, 
                                      String ip) throws IOException
    {
        return httpGet("https://www.dnsmadeeasy.com/servlet/updateip?" +
                       "username=" + username.trim() + 
                       "&password=" + password.trim() + 
                       "&id=" + recordId.trim() + 
                       "&ip=" + ip.trim());
    }
    
	/**
	 * Performs a standard HTTP GET request and returns the result as as String.
	 * 
	 * @param httpGetUrl
	 *            URL to request
	 * @return result of the HTTP GET request as String
	 * @throws IOException
	 *             if there is a problem making the get request
	 */    
    private static String httpGet(String httpGetUrl) throws IOException
    {
        URL url = new URL(httpGetUrl);
        URLConnection urlConn = url.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                urlConn.getInputStream()));
        
        StringBuilder sb = new StringBuilder();
        
        String line = null;
        while ((line = in.readLine()) != null)
        {
            sb.append(line);
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Arguments class for use by JCommander.
     */
    private static class ManualUpdaterArgs
    {
        @Parameter(names = {"-u", "--username"}, required = true, description = "Username for updating record")
        public String username;
        
        @Parameter(names = {"-p", "--password"}, required = true, description = "Password for updating record")
        public String password;
        
        @Parameter(names = {"-r", "--record-id"}, required = true, description = "ID number of DDNS record")
        public String recordId;
        
        @Parameter(names = {"-q", "--quiet"}, description = "No output during update")
        public boolean quiet = false;
    }

    public static void main(String[] args)
    {
        ManualUpdaterArgs cmdArgs = new ManualUpdaterArgs();
        JCommander jCommander = new JCommander(cmdArgs);
        jCommander.setProgramName("manualupdater");
        try
        {
            jCommander.parse(args);
        }
        catch (Exception e)
        {
            jCommander.usage();
            System.exit(1);
        }
        
        // Get public IP using reflector:
        String publicIp = null;
        try
        {
            publicIp = getPublicIp();
            
            if (!cmdArgs.quiet)
            {
                System.out.println("Public IP: <" + publicIp + ">");
            }
        } 
        catch (IOException e)
        {
            System.out.println("Error retrieving public IP.");
            e.printStackTrace();
            System.exit(1);
        }
        
        // Update DDNS record:
        if (!cmdArgs.quiet)
        {
            System.out.println("Updating IP...");
        }
        
        String returnCode = null;
        try
        {
            returnCode = updateDdnsIp(cmdArgs.username, cmdArgs.password, cmdArgs.recordId, publicIp);
        } 
        catch (IOException e)
        {
            System.out.println("Error while updating IP.");
            e.printStackTrace();
            System.exit(1);
        }
        
        if (!cmdArgs.quiet)
        {
            System.out.println(returnCode);
        }
        
        if (returnCode.startsWith("error"))
        {
            System.exit(1);
        }
        else
        {
            System.exit(0);
        }
    }
}
