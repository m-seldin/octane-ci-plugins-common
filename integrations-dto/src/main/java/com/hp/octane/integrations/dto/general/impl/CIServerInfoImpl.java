/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.hp.octane.integrations.dto.general.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hp.octane.integrations.dto.general.CIServerInfo;

/**
 * CIServerInfo DTO implementation.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
class CIServerInfoImpl implements CIServerInfo {
	private String type;
	private String version;
	private String url;
	private String instanceId;
	private Long instanceIdFrom;
	private Long sendingTime = System.currentTimeMillis();

	public CIServerInfoImpl() {
	}

	public CIServerInfoImpl(String type, String version, String url, String instanceId, Long instanceIdFrom) {
		this.type = type;
		this.version = version;
		this.url = normalizeURL(url);
		this.instanceId = instanceId;
		this.instanceIdFrom = instanceIdFrom;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public CIServerInfo setType(String type) {
		this.type = type;
		return this;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public CIServerInfo setVersion(String version) {
		this.version = version;
		return this;
	}

	@Override
	public String getUrl() {
		return url;
	}

	@Override
	public CIServerInfo setUrl(String url) {
		this.url = normalizeURL(url);
		return this;
	}

	@Override
	public String getInstanceId() {
		return instanceId;
	}

	@Override
	public CIServerInfo setInstanceId(String instanceId) {
		this.instanceId = instanceId;
		return this;
	}

	@Override
	public Long getInstanceIdFrom() {
		return instanceIdFrom;
	}

	@Override
	public CIServerInfo setInstanceIdFrom(Long instanceIdFrom) {
		this.instanceIdFrom = instanceIdFrom;
		return this;
	}

	@Override
	public Long getSendingTime() {
		return sendingTime;
	}

	@Override
	public CIServerInfo setSendingTime(Long sendingTime) {
		this.sendingTime = sendingTime;
		return this;
	}

	private String normalizeURL(String input) {
		String result;
		if (input != null && input.endsWith("/")) {
			result = input.substring(0, input.length() - 1);
		} else {
			result = input;
		}
		return result;
	}
}
