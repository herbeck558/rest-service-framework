/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.ConsoleAppender;
import net.logstash.log4j.data.HostData;
import net.minidev.json.JSONObject;

public class CustomAppender<E> extends ConsoleAppender<E> {
	private static final Integer version = 1;
	private static final String KVPrefix = "kv|";
	private static final String NumberPostfix = "_i"; // kv pairs that have keys that end with this are converted to
														// ints
	private static final String ResourceKey = "resource";
	private static final String ApiPath = "/api/";
	private static final String ApiTimeKey = "api_time";
	private static final String ApRCKey = "api_rc";
	private static final String LOG_EVENT_FORMAT = "com_ibm_watson_health_common_log_event_format";
	private static final String LOG_EVENT_FORMAT_JSON = "json";
	private static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS = FastDateFormat
			.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"));
	private static String testDelayProperty = null;

	@Override
	protected void append(E event) {
		if (testDelayProperty == null) {
			try {
				Properties serviceProperties = ServiceContext.getInstance().getServiceProperties();
				testDelayProperty = serviceProperties.getProperty(LOG_EVENT_FORMAT, "");
			} catch (Exception e) {
				// Not initialized, ignore
			}
		}
		try {
			String entry;
			if (testDelayProperty != null && testDelayProperty.equals(LOG_EVENT_FORMAT_JSON)) {
				entry = generateJsonEntry((ILoggingEvent) event);
			} else {
				entry = generateInteractiveEntry((ILoggingEvent) event);
			}
			OutputStream outputStream = getOutputStream();
			byte[] bytes = entry.getBytes("utf-8");
			outputStream.write(bytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String generateInteractiveEntry(ILoggingEvent iLoggingEvent) {
		StringBuffer stringMessage = new StringBuffer();
		stringMessage.append(
				"datetime:" + ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(iLoggingEvent.getTimeStamp()) + ", ");
		String threadName = iLoggingEvent.getThreadName();
		stringMessage.append("thread:" + threadName + ", ");
		stringMessage.append("level:" + iLoggingEvent.getLevel().toString() + ", ");
		stringMessage.append("logger:" + iLoggingEvent.getLoggerName() + ", ");
		Map<String, String> mdcPropertyMap = iLoggingEvent.getMDCPropertyMap();
		for (String key : mdcPropertyMap.keySet()) {
			stringMessage.append(key + ":" + mdcPropertyMap.get(key) + ", ");
		}
		String message = iLoggingEvent.getMessage();
		stringMessage.append("message:" + message + ", ");
		// Add exception data
		if (iLoggingEvent.getThrowableProxy() != null) {
			final IThrowableProxy throwableInformation = iLoggingEvent.getThrowableProxy();
			if (throwableInformation.getClassName() != null) {
				stringMessage.append("exception_class:" + throwableInformation.getClassName() + "\n");
			}
			if (throwableInformation.getMessage() != null) {
				stringMessage.append("exception_message:" + throwableInformation.getMessage() + "\n");
			}
			if (throwableInformation.getStackTraceElementProxyArray() != null) {
				String stackTrace = StringUtils.join(throwableInformation.getStackTraceElementProxyArray(), "\n");
				stringMessage.append("exception_stacktrace:" + stackTrace + "\n");
			}
			IThrowableProxy throwableCause = throwableInformation.getCause();
			while (throwableCause != null) {
				if (throwableCause.getClassName() != null) {
					stringMessage.append("exception_cause_class:" + throwableCause.getClassName() + "\n");
				}
				if (throwableCause.getMessage() != null) {
					stringMessage.append("exception_cause_message:" + throwableCause.getMessage() + "\n");
				}
				if (throwableCause.getStackTraceElementProxyArray() != null) {
					String stackTrace = StringUtils.join(throwableCause.getStackTraceElementProxyArray(), "\n");
					stringMessage.append("exception_cause_stacktrace:" + stackTrace + "\n");
				}
				throwableCause = throwableCause.getCause();
			}
			stringMessage.append(", ");
		}

		stringMessage.setLength(stringMessage.length() - 2);
		return stringMessage.toString();
	}

	private String generateJsonEntry(ILoggingEvent iLoggingEvent) {
		// Add required fields
		JSONObject jsonMessage = new JSONObject();
		jsonMessage.put("@version", version);
		jsonMessage.put("ibm_datetime", ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(iLoggingEvent.getTimeStamp()));

		// Add custom fields
		String hostname = new HostData().getHostName();
		jsonMessage.put("source_host", hostname);
		String message = iLoggingEvent.getMessage();
		jsonMessage.put("message", message);

		// Add exception data
		if (iLoggingEvent.getThrowableProxy() != null) {
			HashMap<String, Object> exceptionInformation = new HashMap<String, Object>();
			final IThrowableProxy throwableInformation = iLoggingEvent.getThrowableProxy();
			if (throwableInformation.getClassName() != null) {
				exceptionInformation.put("exception_class", throwableInformation.getClassName());
			}
			if (throwableInformation.getMessage() != null) {
				exceptionInformation.put("exception_message", throwableInformation.getMessage());
			}
			if (throwableInformation.getStackTraceElementProxyArray() != null) {
				String stackTrace = StringUtils.join(throwableInformation.getStackTraceElementProxyArray(), "\n");
				exceptionInformation.put("stacktrace", stackTrace);
			}
			IThrowableProxy throwableCause = throwableInformation.getCause();
			while (throwableCause != null) {
				if (throwableCause.getClassName() != null) {
					exceptionInformation.put("exception_cause_class", throwableCause.getClassName());
				}
				if (throwableCause.getMessage() != null) {
					exceptionInformation.put("exception_cause_message", throwableCause.getMessage());
				}
				if (throwableCause.getStackTraceElementProxyArray() != null) {
					String stackTrace = StringUtils.join(throwableCause.getStackTraceElementProxyArray(), "\n");
					exceptionInformation.put("exception_cause_stacktrace", stackTrace);
				}
				throwableCause = throwableCause.getCause();
			}
			addAttribute(jsonMessage, "exception", exceptionInformation);
		}

		addAttribute(jsonMessage, "logger_name", iLoggingEvent.getLoggerName());

		Map<String, String> mdcPropertyMap = iLoggingEvent.getMDCPropertyMap();
		JSONObject jsonMdc = new JSONObject();
		for (String key : mdcPropertyMap.keySet()) {
			addAttribute(jsonMdc, key, mdcPropertyMap.get(key));
		}
		addAttribute(jsonMessage, "mdc", jsonMdc);

		addAttribute(jsonMessage, "level", iLoggingEvent.getLevel().toString());
		String threadName = iLoggingEvent.getThreadName();
		addAttribute(jsonMessage, "thread_name", threadName);
		addKVFields(jsonMessage, message);
		addResourceField(jsonMessage, message);

		return jsonMessage.toString();
	}

	/**
	 * Split off fields between kv||. Fields are in the format of field=value and
	 * separated by spaces. e.g. kv|api_rc=200 api_time=0.007 api_verb=GET |
	 */
	private void addKVFields(JSONObject jsonObject, String message) {
		int kvStartIndex = message.indexOf(KVPrefix);
		int kvEndIndex = message.indexOf("|", kvStartIndex + KVPrefix.length());
		if (kvStartIndex >= 0 && kvEndIndex >= 2) {
			String kvString = message.substring(kvStartIndex + KVPrefix.length(), kvEndIndex);
			addFields(jsonObject, kvString, " ", "=");

			String rcValue = (String) jsonObject.get(ApRCKey);
			if (null != rcValue) {
				int rcAsInt = 0;
				try {
					rcAsInt = Integer.parseInt(rcValue);
				} catch (NumberFormatException exception) {
				}
				jsonObject.remove(ApRCKey);
				jsonObject.put(ApRCKey, rcAsInt);
			}
			String timeValue = (String) jsonObject.get(ApiTimeKey);
			if (null != timeValue) {
				Double timeAsDouble = 0.0;
				try {
					timeAsDouble = Double.parseDouble(timeValue);
				} catch (NumberFormatException exception) {
				}
				jsonObject.remove(ApiTimeKey);
				jsonObject.put(ApiTimeKey, timeAsDouble);
			}
		}
	}

	private void addResourceField(JSONObject jsonObject, String message) {
		String resourceVal = null;
		int pathIndex = message.indexOf(ApiPath);
		if (pathIndex >= 0) {
			String pathInfo = message.substring(pathIndex + ApiPath.length() - 1);
			if (pathInfo.length() > 2) {
				int startIndex = pathInfo.indexOf("/");
				if (startIndex >= 0) {
					int endIndex = pathInfo.indexOf("/", startIndex + 1);
					if (endIndex >= 1) {
						resourceVal = pathInfo.substring(endIndex + 1);
						// find end of resource marked by blank or ?
						endIndex = resourceVal.indexOf("?");
						if (endIndex > 0) {
							resourceVal = resourceVal.substring(0, endIndex);
						} else {
							endIndex = resourceVal.indexOf(" ");
							if (endIndex > 0) {
								resourceVal = resourceVal.substring(0, endIndex);
							}
						}
					}
				}
			}
		}
		if (resourceVal != null) {
			addAttribute(jsonObject, ResourceKey, resourceVal);
		}
	}

	private void addFields(JSONObject jsonObject, String fieldList, String fieldSeparator, String valueSeparator) {
		if (null != fieldList) {
			String[] fieldGroups = fieldList.split(fieldSeparator);
			for (String fieldGroup : fieldGroups) {
				String[] field = fieldGroup.split(valueSeparator, 2);
				if (null != field[0]) {
					String key = field[0];
					String value = field[1];
					if (null != value && key.endsWith(NumberPostfix)) {
						int valueAsInt = 0;
						try {
							valueAsInt = Integer.parseInt(value);
						} catch (NumberFormatException exception) {
						}
						addAttribute(jsonObject, key, valueAsInt);
					} else {
						addAttribute(jsonObject, key, value);
					}
				}
			}
		}
	}

	private void addAttribute(JSONObject jsonObject, String keyname, Object keyval) {
		if (null != keyval) {
			jsonObject.put(keyname, keyval);
		}
	}
}
