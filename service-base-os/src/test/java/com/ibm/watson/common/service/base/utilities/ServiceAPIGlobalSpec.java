/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.utilities;

import java.io.File;

public class ServiceAPIGlobalSpec {
	// ###################################################################################################
	// General constants
	public static final String EXP_FOLDER_TYPE = "expectedoutput"; // refers to expectedoutput folder in service project
	public static final String INP_FOLDER_TYPE = "automationInput";// refers to automationInput folder in service project

	public static final String expectedOutputFolder = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "expectedOutput" + File.separator;
}
