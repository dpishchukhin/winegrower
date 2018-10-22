/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.winegrower.service;

import static java.util.Collections.list;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.felix.utils.properties.Properties;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class DefaultConfigurationAdmin implements ConfigurationAdmin {

    private final static String WINEGROWER_CONFIG_PATH = "winegrower.config.path";
    private final static String WINEGROWER_CONFIG_EXTENSION = ".cfg";

    private final Map<String, Configuration> configurations = new HashMap<>();

    @Override
    public Configuration createFactoryConfiguration(final String pid) {
        return new DefaultConfiguration(pid, null, null);
    }

    @Override
    public Configuration createFactoryConfiguration(final String pid, final String location) {
        return new DefaultConfiguration(pid, null, location);
    }

    @Override
    public Configuration getConfiguration(final String pid, final String location) {
        return configurations.computeIfAbsent(pid, p -> new DefaultConfiguration(null, p, location));
    }

    @Override
    public Configuration getConfiguration(final String pid) {
        return configurations.computeIfAbsent(pid, p -> new DefaultConfiguration(null, p, null));
    }

    @Override
    public Configuration[] listConfigurations(final String filter) {
        try {
            final Filter predicate = filter == null ? null : FrameworkUtil.createFilter(filter);
            return configurations.values().stream()
                    .filter(it -> predicate == null || predicate.match(it.getProperties()))
                    .toArray(Configuration[]::new);
        } catch (final InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static class DefaultConfiguration implements Configuration {
        private final String factoryPid;
        private final String pid;
        private String location;
        private final Hashtable<String, Object> properties;
        private final AtomicLong changeCount = new AtomicLong();

        private DefaultConfiguration(final String factoryPid, final String pid, final String location) {
            this.factoryPid = factoryPid;
            this.pid = pid;
            this.location = location;
            this.properties = new Hashtable<>();
            // try to load properties for external file
            // first check using the winegrower.config.path system properties
            if (System.getProperty(WINEGROWER_CONFIG_PATH) != null) {
                File file = new File(System.getProperty(WINEGROWER_CONFIG_PATH), pid + WINEGROWER_CONFIG_EXTENSION);
                if (file.exists()) {
                    try {
                        Properties properties = new Properties(file);
                        this.properties.putAll(properties);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            // second, we fallback to look for the config file in the classpath
            } else if (this.getClass().getResourceAsStream("/" + pid + WINEGROWER_CONFIG_EXTENSION) != null) {
                Properties properties = new Properties();
                try {
                    properties.load(this.getClass().getResourceAsStream("/" + pid + WINEGROWER_CONFIG_EXTENSION));
                    this.properties.putAll(properties);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            // finally, we fallback to system properties
            } else {
                final String prefix = "winegrower.service." + pid + ".";
                System.getProperties().stringPropertyNames().stream()
                    .filter(it -> it.startsWith(prefix))
                    .forEach(key -> properties.put(key.substring(prefix.length()), System.getProperty(key)));
            }
        }

        @Override
        public String getPid() {
            return pid;
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            return properties;
        }

        @Override
        public void update(final Dictionary<String, ?> properties) {
            this.properties.clear();
            if (System.getProperty(WINEGROWER_CONFIG_PATH) != null) {
                File file = new File(System.getProperty(WINEGROWER_CONFIG_PATH, pid + WINEGROWER_CONFIG_EXTENSION));
                try {
                    Properties prop = new Properties(file);
                    prop.update(converter(properties));
                    prop.store(new FileOutputStream(file), null);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } else if (this.getClass().getResourceAsStream("/" + pid + ".cfg") != null) {
                Properties prop = new Properties();
                try {
                    prop.load(this.getClass().getResourceAsStream("/" + pid + ".cfg"));
                    prop.update(converter(properties));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } else {
                list(properties.keys()).forEach(key -> this.properties.put(key, properties.get(key)));
            }
            this.changeCount.incrementAndGet();
        }

        private Map<String, String> converter(Dictionary<String, ?> properties) {
            Map<String, String> map = new HashMap<>();
            Enumeration<String> keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                map.put(key, properties.get(key).toString());
            }
            return map;
        }

        @Override
        public void delete() {
            // no-op
        }

        @Override
        public String getFactoryPid() {
            return factoryPid;
        }

        @Override
        public void update() {
            if (System.getProperty(WINEGROWER_CONFIG_PATH) != null) {
                File file = new File(System.getProperty(WINEGROWER_CONFIG_PATH, pid + WINEGROWER_CONFIG_EXTENSION));
                try {
                    Properties prop = new Properties(file);
                    prop.update(converter(properties));
                    prop.store(new FileOutputStream(file), null);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } else if (this.getClass().getResourceAsStream("/" + pid + ".cfg") != null) {
                Properties prop = new Properties();
                try {
                    prop.load(this.getClass().getResourceAsStream("/" + pid + ".cfg"));
                    prop.update(converter(properties));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            this.changeCount.incrementAndGet();
        }

        @Override
        public void setBundleLocation(final String location) {
            this.location = location;
        }

        @Override
        public String getBundleLocation() {
            return location;
        }

        @Override
        public long getChangeCount() {
            return changeCount.get();
        }
    }
}