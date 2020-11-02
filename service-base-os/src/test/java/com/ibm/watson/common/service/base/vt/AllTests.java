/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.vt;

import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.ClassnameFilters;
import org.junit.extensions.cpsuite.ClasspathSuite.ClasspathProperty;
import org.junit.extensions.cpsuite.ClasspathSuite.IncludeJars;
import org.junit.runner.RunWith;

@RunWith(ClasspathSuite.class)
@ClasspathProperty("test.bvtclasspath")
@ClassnameFilters({"!.*\\$.*"}) // to avoid any inner classes references within the junit testcases.
@IncludeJars(true)
public class AllTests {
	// Testsuite using classpathsuite library to suite up all junit tests that are found in the classpath of the project.
}
