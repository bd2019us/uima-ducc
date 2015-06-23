/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.ws.utils.alien;

import java.io.File;

public class FileInfo {
	
	public String name;
	public long length;
	
	public FileInfo(String name, long length) {
		setName(name);
		setLength(length);
	}
	
	public void setName(String value) {
		name = value;
	}
	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		String retVal = name;
		if(name != null) {
			retVal = name.substring(name.lastIndexOf(File.separator)+1);
		}
		return retVal;
	}
	
	public void setLength(long value) {
		length = value;
	}
	
	public long getLength() {
		return length;
	}
}