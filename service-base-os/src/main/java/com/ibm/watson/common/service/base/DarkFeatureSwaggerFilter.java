/*
 * (C) Copyright IBM Corp. 2017, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.core.filter.SwaggerSpecFilter;
import io.swagger.model.ApiDescription;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;

/**
 * A Swagger filter that hides operations and parameters that are not appropriate in multi tenant mode.
 *
 * The "multi-tenant-mode" system property is queried to determine if the system is running in multi-tenant mode.
 *
 * To register this filter, add the following to your ServiceContextListener:
 * beanConfig.setFilterClass(MultiTenantSwaggerFilter.class.getName());
 *
 * To hide an operation in multi-tenant mode, add this annotation to the method:
 *
 * @ApiImplicitParams(@ApiImplicitParam(access = MultiTenantSwaggerFilter.MULTI_TENANT_DISABLED, name =
 *                                             MultiTenantSwaggerFilter.MULTI_TENANT_PARAM, paramType="header",
 *                                             dataType="string"))
 *
 *                                             To hide a parameter in multi-tenant mode, add access to your @ApiParam as
 *                                             follows: @ApiParam(..., access=MultiTenantSwaggerFilter.MULTI_TENANT_DISABLED)
 *
 */
public class DarkFeatureSwaggerFilter implements SwaggerSpecFilter {
	public static final String ENABLED_DARK_FEATURES = "enabled.dark.features";
	public static final String DARK_FEATURE_CONTROLLED = "darkFeatureControlled";
	public static final String DARK_FEATURE_NAME = "darkFeature";

	private static Set<String> enabledDarkFeatureSet;

	static {
		String enabledDarkFeaturesProperty = System.getProperty(ENABLED_DARK_FEATURES, "none");
		if (enabledDarkFeaturesProperty.equalsIgnoreCase("all")) {
			enabledDarkFeatureSet = null;
		} else {
			enabledDarkFeatureSet = new HashSet<String>();
			if (!enabledDarkFeaturesProperty.equalsIgnoreCase("none")) {
				String[] values = enabledDarkFeaturesProperty.split(",");
				enabledDarkFeatureSet.addAll(Arrays.asList(values));
			}
		}
	}

	public DarkFeatureSwaggerFilter() {
	}

	@Override
	public boolean isOperationAllowed(Operation operation, ApiDescription api, Map<String, List<String>> params, Map<String, String> cookies,
			Map<String, List<String>> headers) {
		for (Parameter parameter : operation.getParameters()) {
			if (parameter instanceof HeaderParameter) {
				String access = parameter.getAccess();
				if (access != null && access.equals(DARK_FEATURE_CONTROLLED)) {
					Map<String, Object> extensions = operation.getVendorExtensions();
					if (extensions != null) {
						String darkFeatureName = (String) extensions.get("x-" + DARK_FEATURE_NAME);
						if (!ServiceContext.isDarkFeatureEnabled(darkFeatureName)) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	@Override
	public boolean isParamAllowed(Parameter parameter, Operation operation, ApiDescription api, Map<String, List<String>> params, Map<String, String> cookies,
			Map<String, List<String>> headers) {
		String access = parameter.getAccess();
		if (access != null && access.equals(DARK_FEATURE_CONTROLLED)) {
			Map<String, Object> extensions = operation.getVendorExtensions();
			if (extensions != null) {
				String darkFeatureName = (String) extensions.get("x-" + DARK_FEATURE_NAME);
				if (!ServiceContext.isDarkFeatureEnabled(darkFeatureName)) {
					return false;
				}
			}
		}
		if (parameter instanceof HeaderParameter) {
			if (access != null && access.equals(DARK_FEATURE_CONTROLLED)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isPropertyAllowed(Model model, Property property, String propertyName, Map<String, List<String>> params, Map<String, String> cookies,
			Map<String, List<String>> headers) {
		return true;
	}

}
