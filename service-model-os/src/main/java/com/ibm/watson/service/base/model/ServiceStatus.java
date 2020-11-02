/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.service.base.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@XmlRootElement(name="serviceStatus")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(value="service status", description="Object representing service runtime status")
public class ServiceStatus {

	public static enum ServiceState {OK, WARNING, ERROR};

	@XmlElement
	@ApiModelProperty(value="version of the service")
	protected String version;
	@XmlElement
	@ApiModelProperty(value="service uptime since last restart")
	protected String upTime;
	@XmlElement
	@ApiModelProperty(value="scurrent service state")
	protected ServiceState serviceState;
	@XmlElement
	@ApiModelProperty(value="service state details")
	protected String stateDetails;  // Additional details about state
	@XmlElement
	@ApiModelProperty(value="service uptime since last restart")
	protected String hostName;
	@XmlElement
	@ApiModelProperty(value="total number of requests during uptime")
	protected Long requestCount;
	@XmlElement
	@ApiModelProperty(value="Maximum memory used during uptime")
	protected Long maxMemoryMb;
	@XmlElement
	@ApiModelProperty(value="Megabytes of committed memory")
	protected Long commitedMemoryMb;
	@XmlElement
	@ApiModelProperty(value="Megabytes of memory used")
	protected Long inUseMemoryMb;
	@XmlElement
	@ApiModelProperty(value="number of available processors")
	protected Integer availableProcessors;
	@XmlElement
	@ApiModelProperty(value="number of concurrent requests")
	protected Integer concurrentRequests;
	@XmlElement
	@ApiModelProperty(value="configured maximum concurrent request limit")
	protected Integer maxConcurrentRequests;
	@XmlElement
	@ApiModelProperty(value="number of rejected requests")
	protected Long totalRejectedRequests;
	@XmlElement
	@ApiModelProperty(value="number of blocked requests")
	protected Long totalBlockedRequests;


	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}

	public String getUpTime() {
		return upTime;
	}
	public void setUpTime(String upTime) {
		this.upTime = upTime;
	}

	public ServiceState getServiceState() {
		return serviceState;
	}
	public void setServiceState(ServiceState serviceState) {
		this.serviceState = serviceState;
	}

	public String getStateDetails() {
		return stateDetails;
	}
	public void setStateDetails(String stateDetails) {
		this.stateDetails = stateDetails;
	}

	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public Long getRequestCount() {
		return requestCount;
	}
	public void setRequestCount(Long requestCount) {
		this.requestCount = requestCount;
	}

	public Long getMaxMemoryMb() {
		return maxMemoryMb;
	}
	public void setMaxMemoryMb(Long totalMemory) {
		this.maxMemoryMb = totalMemory;
	}

	public Long getCommitedMemoryMb() {
		return commitedMemoryMb;
	}
	public void setCommitedMemoryMb(Long freeMemoryMb) {
		this.commitedMemoryMb = freeMemoryMb;
	}

	public Long getInUseMemoryMb() {
		return inUseMemoryMb;
	}
	public void setInUseMemoryMb(Long inUseMemoryMb) {
		this.inUseMemoryMb = inUseMemoryMb;
	}

	public Integer getAvailableProcessors() {
		return availableProcessors;
	}
	public void setAvailableProcessors(int availableProcessors) {
		this.availableProcessors = availableProcessors;
	}

	public Integer getConcurrentRequests() {
		return concurrentRequests;
	}
	public void setConcurrentRequests(Integer concurrentRequests) {
		this.concurrentRequests = concurrentRequests;
	}

	public Integer getMaxConcurrentRequests() {
		return maxConcurrentRequests;
	}
	public void setMaxConcurrentRequests(Integer maxConcurrentRequests) {
		this.maxConcurrentRequests = maxConcurrentRequests;
	}

	public Long getTotalRejectedRequests() {
		return totalRejectedRequests;
	}
	public void setTotalRejectedRequests(Long totalRejectedRequests) {
		this.totalRejectedRequests = totalRejectedRequests;
	}

	public Long getTotalBlockedRequests() {
		return totalBlockedRequests;
	}
	public void setTotalBlockedRequests(Long totalBlockedRequests) {
		this.totalBlockedRequests = totalBlockedRequests;
	}
}
