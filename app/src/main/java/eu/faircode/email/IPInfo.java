package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2020 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.net.MailTo;
import android.net.ParseException;
import android.net.Uri;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class IPInfo {
    private static Map<InetAddress, Organization> addressOrganization = new HashMap<>();

    private final static int FETCH_TIMEOUT = 15 * 1000; // milliseconds

    static Pair<String, Organization> getOrganization(Uri uri, Context context) throws IOException, ParseException {
        if ("mailto".equals(uri.getScheme())) {
            MailTo email = MailTo.parse(uri.toString());
            String to = email.getTo();
            if (to == null || !to.contains("@"))
                throw new UnknownHostException();
            String domain = to.substring(to.indexOf('@') + 1);
            InetAddress address = DnsHelper.lookupMx(context, domain);
            if (address == null)
                throw new UnknownHostException();
            return new Pair<>(domain, getOrganization(address));
        } else {
            String host = uri.getHost();
            if (host == null)
                throw new UnknownHostException();
            InetAddress address = InetAddress.getByName(host);
            return new Pair<>(host, getOrganization(address));
        }
    }

    private static Organization getOrganization(InetAddress address) throws IOException {
        synchronized (addressOrganization) {
            if (addressOrganization.containsKey(address))
                return addressOrganization.get(address);
        }

        // https://ipinfo.io/developers
        URL url = new URL("https://ipinfo.io/" + address.getHostAddress());
        Log.i("GET " + url);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setReadTimeout(FETCH_TIMEOUT);
        connection.connect();

        Organization organization = new Organization();
        try {
            String json = Helper.readStream(connection.getInputStream(), StandardCharsets.UTF_8.name());
            /* {
                "ip": "8.8.8.8",
                    "hostname": "dns.google",
                    "city": "Mountain View",
                    "region": "California",
                    "country": "US",
                    "loc": "37.4056,-122.0775",
                    "org": "AS15169 Google LLC",
                    "postal": "94043",
                    "timezone": "America/Los_Angeles"
            } */

            JSONObject jinfo = new JSONObject(json);
            organization.name = jinfo.optString("org");
            organization.country = jinfo.optString("country");
        } catch (JSONException ex) {
            throw new IOException(ex);
        } finally {
            connection.disconnect();
        }

        synchronized (addressOrganization) {
            addressOrganization.put(address, organization);
        }

        return organization;
    }

    static class Organization {
        String name;
        String country;
    }
}
