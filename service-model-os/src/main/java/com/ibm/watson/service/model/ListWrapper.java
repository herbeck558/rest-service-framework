/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.service.model;

import java.util.HashMap;
import java.util.List;

/**
 * Generic list wrapper that allows you to control the key name associated with your list.
 * For example,
 *    new ListWrapper<Cartridge>("cartridges", cartridgeList)
 * will serialize as:
 *    {
 *       "cartridges": [
 *           {
 *              "name": "My cartridge"
 *           }
 *       ]
 *    }
 *
 * @param <T>
 */
public class ListWrapper<T> extends HashMap<String,List<T>>{
	private static final long serialVersionUID = 3733743233694308563L;

	public ListWrapper(String key, List<T> list) {
		super();
		super.put(key, list);
	}
}
