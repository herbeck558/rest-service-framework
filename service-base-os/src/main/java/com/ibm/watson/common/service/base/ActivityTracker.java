/*
 * (C) Copyright IBM Corp. 2018, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.ibm.watson.common.service.base.security.MainServletFilter;

import net.minidev.json.JSONObject;

/**
 * Log activity events (user-initiated activities that change the state of a service).
 * See https://console.test.cloud.ibm.com/docs/services/Activity-Tracker-with-LogDNA/ibm-internal-only/event_definition.html#ibm_event_fields
 * for details on fields.
 */
public class ActivityTracker {
	private static final Logger atlogger = LoggerFactory.getLogger("activitytracker");
	private static final Logger logger = LoggerFactory.getLogger(ActivityTracker.class.getName());
	private static final String PROJECT_CLOUD_ID = "com_ibm_watson_health_common_project_cloud_id";
	private static final String crnSeparator = ":";
	private static final int crnResourceTypeIndex = 8;
	private static final int crnResourceIndex = 9;

	private static Boolean activityTrackerStartupExecuted = false;
	private static ActivityTracker activityTrackerInstance = null;
	private static ServiceContext serviceContext = null;
	private static String projectID = null;

	public enum Action { //action that triggers an event
		create,
		read,
		update,
		delete,
		backup,
		capture,
		configure,
		deploy,
		disable,
		enable,
		monitor,
		restore,
		start,
		stop,
		undeploy,
		receive,
		send,
		authenticate,
		renew,
		revoke,
		allow,
		deny,
		evaluate,
		notify,
		unknown
	};
	public enum Outcome { //the result of the action
		success,
		pending,
		failure,
		unknown
	};

	public enum Severity {
		normal,   // routine actions
		warning,  // resource is updated or its metadata is modified
		critical  // action affects security like changing credentials of a user, deleting data
	};

	public static final String ORIGINATING_SOURCE_HOST_IP_HEADER = "CF-Connecting-IP";
	public static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final FastDateFormat MILLI_FORMAT = FastDateFormat.getInstance("SS", UTC);
	private static String formatMillisWithPrecisionTwo(long timestamp) {
		// get exactly two bits of precision for the millisecond.
		// FastDateFormat.getInstance("SS", UTC).format() can return things like "3", "37", or "374", so we
		// pad with two zeros and take the lenth-2 prefix.
		String formattedDate = MILLI_FORMAT.format(timestamp) + "00"; // add extra zeros on the end in case we hit an early timestamps (millis=6, 67)
		return formattedDate.substring(0, 2);
	}
	private static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss", UTC);
	public static String dateFormat(long timestamp) {
		// the java date formatter doesn't respect a request for only two bits of milli precision 'SS', but this is
		// required by AT. So we do it manually and then and then append zulu marker +0000
		String formattedDate = ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(timestamp);
		return formattedDate + "." + formatMillisWithPrecisionTwo(timestamp) + "+0000";
	}

	private enum UserType {
		user,
		serviceid,
		clientid
	}

	private ActivityTracker(){}

	/**
	 * Creates an instance of ActivityTracker if not already started.
	 * @return ActivityTracker instance
	 */
	public static ActivityTracker createInstance() {
		if ( ! activityTrackerStartupExecuted) {
			serviceContext = ServiceContext.getInstance();
			projectID = serviceContext.getServiceProperties().getProperty(PROJECT_CLOUD_ID);
			ActivityTracker instance = new ActivityTracker();
			activityTrackerInstance = instance;
			activityTrackerStartupExecuted = true;
		}
		return activityTrackerInstance;
	}

	/**
	 * Retrieves an ActivityTracker instance.  Will create an instance if one does not exist.
	 * @return ActivityTracker instance
	 */
	public static ActivityTracker getInstance() {
		if (! activityTrackerStartupExecuted) {
			createInstance();
		}
		return activityTrackerInstance;
	}

	/**
	 * Releases the ActivityTracker instance
	 */
	public void release() {
		/**
		 * At the application shutdown
		 */
		activityTrackerStartupExecuted = false;
		activityTrackerInstance = null;
	}

	public void logEvent(HttpServletRequest request, Action action, Severity severity, Integer reasonCode, Outcome outcome, String objectType, String objectName) {
		boolean dataEventFlag = false;
		logEvent(request, action, severity, reasonCode, outcome, objectType, objectName, dataEventFlag);
	}

	public void logEvent(HttpServletRequest request,
		Action action,
		Severity severity,
		Integer reasonCode,
		Outcome outcome,
		String objectType,
		String objectName,
		boolean dataEventFlag) {

		String initialValue = null;
		String newValue = null;
		logEvent(request, action, severity, reasonCode, outcome, objectType, objectName, dataEventFlag,
			initialValue, newValue);
	}

	public void logEvent(HttpServletRequest request,
		Action action,
		Severity severity,
		Integer reasonCode,
		Outcome outcome,
		String objectType,
		String objectName,
		boolean dataEventFlag,
		String initialValue,
		String newValue) {

		String resourceType = null;
		String reasonForFailure = null;
		logEvent(request, action, severity, reasonCode, outcome, objectType, objectName, dataEventFlag,
			initialValue, newValue, resourceType, reasonForFailure);
		}

	/**
	 *
	 * Logs an event.
	 * objectName will be used in the 'resource' segment of the CRN so it
	 * "MUST be a be a GUID or a string encoded according to the URI syntax as described in
	 * RFC 3986 Uniform Resource Identifier (URI): Generic Syntax,Section 2.
	 * In particular the "/" character can be used to represent a hierarchical path."
	 *
	 * Args
	 *  * dataEventFlag - controls the "management event" and the "data event"
	 *   * Set this to false to get only the "management event"
	 *   * Set this to true  to get both the "management event" and the "data event"
	 *   * If the dataEventFlag is true, then the saveServiceCopy has to be false so there is no info overload.
	 * 	 * https://test.cloud.ibm.com/docs/services/Activity-Tracker-with-LogDNA?topic=logdnaat-ibm_event_fields
     *   * https://test.cloud.ibm.com/docs/Activity-Tracker-with-LogDNA/ibm-internal-only?topic=logdnaat-ibm_event_fields#dataEvent
	 *
	 **/
	public void logEvent(HttpServletRequest request,
		Action action,
		Severity severity,
		Integer reasonCode,
		Outcome outcome,
		String objectType,
		String objectName,
		boolean dataEventFlag,
		String initialValue,
		String newValue,
		String resourceType,
		String reasonForFailure) {

		//Required fields:0
		//  initiator.id, initiator.name, initiator.typeURI, initiator.credential.type, initiator.host.address
		//  target.id, target.name, target.typeURI
		//  action, outcome, reason.reasonCode, reason.reasonType, severity, eventTime
		//Required for new architecture:
		//  logSourceCRN, saveServiceCopy, message

		if (isEnabled()) {
			Instant instant = Instant.now();

			//need to look at header info to get user account id, service's crn, etc
			//bluemix-iamid for account id
			//bluemix-crn for the servcie instance CRN
			Map<String,String> headerInfo = getWatsonUserInfo();

			String instanceCRN = headerInfo.get("bluemix-crn");
			String serviceProviderName = getServiceProviderName(instanceCRN);
			String serviceCatalogName = getCatalogName(instanceCRN);

			String credentialType = "apikey"; //token, user, or apikey
			UserType userType = getUserType(headerInfo.get("bluemix-iamid")); //determined by prefix of bluemix-iamid or bluemix-subject
			//initialtorType format is: service/security/clientid, service/security/account/user or service/security/account/serviceid
			String initiatorType = "service/security/account/" + userType.name();
			String initatorID = headerInfo.get("bluemix-iamid");
			String initiatorName = headerInfo.get("bluemix-subject");;
			String initiatorSourceIPAddress = getSourceIPAddress(request);

			logger.debug("serviceProperties = " + serviceContext.getServiceProperties());

			Map<String, Object> payload = new HashMap<>();

			String actionMsg = serviceProviderName;
			if (actionMsg != null) {
				actionMsg = actionMsg + "." + objectType + "." + action.name();
			} else {
				actionMsg = objectType + "." + action.name();
			}
			payload.put("action", actionMsg);

			payload.put("dataEvent", dataEventFlag);
			
			payload.put("eventTime", dateFormat(instant.toEpochMilli()));

			if (instanceCRN != null) {
				payload.put("logSourceCRN", instanceCRN);
			}

			//   format is:    serviceName: action objectType target.name [custom data per service][-outcome]
			String message = serviceCatalogName;
			if (message != null) {
				message = message + ": " + action.name() + " " + objectType + " ";
				if (objectName != null) {
					message = message + objectName;
				} else if (serviceProviderName != null) {
					message = message + serviceProviderName;
				}
				if (outcome != Outcome.success) {
					message = message + " -" + outcome.name();
				}
			} else {
				message = ": " + action.name() + " " + objectType;
			}
			payload.put("message", message);

			Map<String, Object> initiator = new HashMap<>();
			if (initatorID != null) {
				initiator.put("id", initatorID);
			}
			if (initiatorName != null) {
				initiator.put("name", initiatorName);
			}
			if (initiatorType != null) {
				initiator.put("typeURI", initiatorType);
			}
			if (credentialType != null) {
				Map<String, String> credential = new HashMap<>();
				credential.put("type", credentialType);
				initiator.put("credential", credential);
			}
			Map<String, String> host = new HashMap<>();
			//initiator.host.address: originating IP address
			if (initiatorSourceIPAddress != null) {
				host.put("address", initiatorSourceIPAddress);
				initiator.put("host", host);
			}
			payload.put("initiator", initiator);

			Map<String, String> observer = new HashMap<>();
			observer.put("name", "ActivityTracker");
			payload.put("observer", observer);
			
			payload.put("outcome", outcome.name());

			Map<String, String> reason = new HashMap<>();
			reason.put("reasonCode", reasonCode.toString());
			Status status = Status.fromStatusCode(reasonCode);
			if (status != null) {
				reason.put("reasonType", status.toString());
			}
			payload.put("reason", reason);

			Map<String, Object> requestData = new HashMap<>();
			//String resourceGroupId = headerInfo.get("bluemix-resource-group");
			//if (resourceGroupId != null) {
			//	requestData.put("resourceGroupId", resourceGroupId)
			//}
			if (action == ActivityTracker.Action.update) {
				requestData.put("updateType", StringUtils.capitalize(objectType.toString()) + " changed");
				if (initialValue != null) {
					requestData.put("initialValue", initialValue);
				}
				if (newValue != null) {
					requestData.put("newValue", newValue);
				}
			}
			String correlationId = (String)MDC.get(MainServletFilter.CORRELATION_ID_KEY);
			if (correlationId != null) {
				requestData.put("requestId", correlationId);
			}
			if (reasonForFailure!= null) {
				requestData.put("reasonForFailure", reasonForFailure);
			}
			if (resourceType!= null) {
				requestData.put("resourceType", resourceType);
			}
			payload.put("requestData", requestData);

			Map<String, Object> responseData = new HashMap<>();
			payload.put("responseData", responseData);

			boolean saveServiceCopyValue = (dataEventFlag == true) ? false : true;
			payload.put("saveServiceCopy", saveServiceCopyValue);

			payload.put("severity", severity.name());

			Map<String, String> target = new HashMap<>();
			if (instanceCRN != null) {
				String resourceCRN = addResourceValuesTo(instanceCRN, objectType, objectName);
				target.put("id", resourceCRN);
			}
			
			if (objectName != null) {
				target.put("name", objectName);
			}
			else if (serviceProviderName != null) {
				target.put("name", serviceProviderName);
			}
			
			String typeURI = serviceProviderName;
			if (typeURI != null) {
				typeURI = typeURI + "/" + objectType;
			} else {
				typeURI = objectType;
			}
			target.put("typeURI", typeURI);
			payload.put("target", target);

			JSONObject event = new JSONObject();
			event.put("payload", payload);
			atlogger.info(event.toString());
		}
	}

	/*
	 * Returns CRN with resource type and name updated to given values.
	 */
	private String addResourceValuesTo(String CRN, String resourceType, String resourceName) {
		ArrayList<String> values = new ArrayList<String>(Arrays.asList(CRN.split(crnSeparator)));
		logger.debug("crn before adding resource values:" + values.toString());

		if (crnResourceTypeIndex <= values.size()) {
			values.add(crnResourceTypeIndex, resourceType);
		}
		if (crnResourceIndex <= values.size()) {
			values.add(crnResourceIndex, resourceName);
		}
		String newCRN = String.join(crnSeparator, values);
		logger.debug("crn after adding resource values:" + newCRN);
		return newCRN;
	}

	private String getCatalogName(String CRN) {
		if (CRN!= null && !CRN.isEmpty() && CRN.contains("wh-iml")) {
			return "Insights for Medical Literature";
		}
		else {
			return "Annotator for Clinical Data";
		}
	}

	private String getServiceProviderName(String fromCRN) {
		int indexPosition = 4; //5th field of CRN, index starts at 0
		String name = null;
		if (fromCRN != null) {
			//split by : and pull out 5th value
			String[] values = fromCRN.split(crnSeparator);
			if (values.length > indexPosition) {
				name = values[indexPosition];
			}
		}
		return name;
	}

	private UserType getUserType(String iamID) {
		UserType userType = UserType.user;
		if (iamID!=null && iamID.toLowerCase().contains("serviceid-")) {
			userType = UserType.serviceid;
		}
		return userType;
	}

    private String getSourceIPAddress(HttpServletRequest request) {
		if (request != null) {
			String ipAddress = request.getHeader(ORIGINATING_SOURCE_HOST_IP_HEADER);
			logger.debug("ActivityTracker found originating ipAddress of " + ipAddress);
			return ipAddress;
		} else {
			logger.warn("ActivityTracker found no ipAddress value.");
			return null;
		}
	}

	private Map<String,String> getWatsonUserInfo() {
		Map<String,String> requestHeaders = ServiceThreadLocal.getRequestHeaders();
		Map<String,String> userInfoMap = new HashMap<String,String>();
		String watsonUserInfo = null;
		if(requestHeaders != null) {
			watsonUserInfo = requestHeaders.get(MainServletFilter.HEADER_WATSON_USER_INFO);
		} else {
			logger.warn("ActivityTracker - No requestHeaders so unable to get info needed for activity tracker.");
		}
		if (watsonUserInfo != null) {
			String[] wuiValues = watsonUserInfo.split(";");
			for (int i = 0 ; i < wuiValues.length ; ++i) {
				String[] nameValue = wuiValues[i].split("=");
				if (nameValue.length != 2) {
					logger.error("Unrecognized user info header value: "+nameValue);
					continue;
				}
				userInfoMap.put(nameValue[0], nameValue[1]);
				//logger.warn("ActivityTracker - name/value = " + nameValue[0] + "/" + nameValue[1]);
			}

		} else {
			// no header provided, so won't be able to properly log
			logger.warn("ActivityTracker - No Watson USER INFO header passed in with request so no logging can be done.");
			//return;
		}

		return userInfoMap;
	}


	/**
	 * Check if activity tracker logging is enabled for this service deployment environment
	 * @return true if activity tracking is enabled
	 */
	public boolean isEnabled() {
		//project id property is only defined for Cloud deployments where there is an activity tracker setup
		return projectID != null;
	}

}
