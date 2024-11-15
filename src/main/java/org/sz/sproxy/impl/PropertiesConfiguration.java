/*
 * Copyright (c) 2022 Sam Zheng                                            
 *                                                                         
 * Licensed under the Apache License, Version 2.0 (the "License");         
 * you may not use this file except in compliance with the License.        
 * You may obtain a copy of the License at                                 
 *                                                                         
 *     http://www.apache.org/licenses/LICENSE-2.0                          
 *                                                                         
 * Unless required by applicable law or agreed to in writing, software     
 * distributed under the License is distributed on an "AS IS" BASIS,       
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and     
 * limitations under the License.                                          
 */
package org.sz.sproxy.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.sz.sproxy.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sam Zheng
 *
 */
@Slf4j
public class PropertiesConfiguration implements Configuration {
	
	Properties props;
	
	public PropertiesConfiguration() {
		props = new Properties(); // empty, all with default
	}
	
	public PropertiesConfiguration(String file) throws IOException {
		this(new FileInputStream(file));
	}
	
	public PropertiesConfiguration(Properties props) {
		Objects.requireNonNull(props, "configuration properties is null");
		this.props = (Properties) props.clone();
		log.debug("configuration: " + props);
	}
	
	public PropertiesConfiguration(InputStream in) throws IOException {
		props = new Properties();
		props.load(in);
		log.debug("configuration: " + props);
	}

	@Override
	public String get(String key, String def) {
		String v = props.getProperty(key);
		return Optional.ofNullable(v).orElseGet(() -> getSystemProperty(key, def));
	}
	
	private String getSystemProperty(String key, String def) {
		String v = System.getProperty(key);
		return Optional.ofNullable(v).orElse(def);
	}
	
	@Override
	public String set(String key, String value) {
		return (String) props.setProperty(key, value);
	}
	
	private int bufSize = -1;
	@Override
	public int getPacketBufferSize() {
		if (bufSize == -1) {
			bufSize = Configuration.super.getPacketBufferSize();
		}
		return bufSize;
	}

}
