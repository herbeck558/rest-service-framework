/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.security;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.apache.commons.lang3.StringEscapeUtils;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;

public class XssCharEscapes extends CharacterEscapes {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public int[] getEscapeCodesForAscii() {
		int[] xssCharEscapes;
		xssCharEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
		xssCharEscapes['&'] = CharacterEscapes.ESCAPE_CUSTOM;
		xssCharEscapes['<'] = CharacterEscapes.ESCAPE_CUSTOM;
		xssCharEscapes['>'] = CharacterEscapes.ESCAPE_CUSTOM;
		xssCharEscapes['"'] = CharacterEscapes.ESCAPE_CUSTOM;
		xssCharEscapes['\''] = CharacterEscapes.ESCAPE_CUSTOM;
		xssCharEscapes['/'] = CharacterEscapes.ESCAPE_CUSTOM;
		return xssCharEscapes;
	}

	@Override
	public SerializableString getEscapeSequence(int ch) {
		return new SerializableString() {

			String str = Character.toString((char)ch);

			@Override
			public int writeUnquotedUTF8(OutputStream out) throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int writeQuotedUTF8(OutputStream out) throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int putUnquotedUTF8(ByteBuffer out) throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int putQuotedUTF8(ByteBuffer buffer) throws IOException {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public String getValue() {
				switch(str) {
					case "'":
					case "/":
					return "&#x" + Integer.toHexString(ch) + ';';
				}
				return StringEscapeUtils.escapeHtml4(str);
			}

			@Override
			public int charLength() {
				// TODO Auto-generated method stub
				return str.length();
			}

			@Override
			public byte[] asUnquotedUTF8() {
				// TODO Auto-generated method stub
				return new byte[0];
			}

			@Override
			public byte[] asQuotedUTF8() {
				// TODO Auto-generated method stub
				return new byte[0];
			}

			@Override
			public char[] asQuotedChars() {
				// TODO Auto-generated method stub
				return new char[0];
			}

			@Override
			public int appendUnquotedUTF8(byte[] buffer, int offset) {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int appendUnquoted(char[] buffer, int offset) {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int appendQuotedUTF8(byte[] buffer, int offset) {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int appendQuoted(char[] buffer, int offset) {
				// TODO Auto-generated method stub
				return 0;
			}
		};
	}

}
