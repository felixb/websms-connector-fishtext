/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.websms.connector.fishtext;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.WebSMSException;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;

/**
 * AsyncTask to manage IO to fishtext.com API.
 * 
 * @author flx
 */
public class ConnectorFishtext extends Connector {
	/** Tag for output. */
	private static final String TAG = "fishtext";
	/** {@link SubConnectorSpec} ID: basic. */

	/** Fishtext URL: login. */
	private static final String URL_LOGIN = // .
	"https://www.fishtext.com/cgi-bin/account";
	/** Fishtext URL: presend. */
	private static final String URL_PRESEND = // .
	"https://www.fishtext.com/cgi-bin/ajax/sendMessage.cgi";
	/** Fishtext URL: send. */
	private static final String URL_SEND = // .
	"https://www.fishtext.com/SendSMS/SendSMS";

	/** Check for balance. */
	private static final String CHECK_BALANCE = "Current balance:";
	/** Check for presend. */
	private static final String CHECK_PRESEND = "messagelargeinput";
	/** Check for sent. */
	private static final String CHECK_SENT = "Message sent";

	/** Stip bytes from stream: login. */
	private static final int STRIP_LOGIN_START = 4500;
	/** Stip bytes from stream: login. */
	private static final int STRIP_LOGIN_END = 6500;
	/** Stip bytes from stream: presend. */
	// private static final int STRIP_PRESEND_START = 1000;
	/** Stip bytes from stream: presend. */
	// private static final int STRIP_PRESEND_END = 3000;

	/** Number of vars pushed at login. */
	private static final int NUM_VARS_LOGIN = 4;
	/** Number of vars pushed at send. */
	private static final int NUM_VARS_SEND = 6;

	/** HTTP Useragent. */
	private static final String TARGET_AGENT = "Mozilla/5.0 (Windows; U; "
			+ "Windows NT 5.1; ko; rv:1.9.2.3) Gecko/20100401 Firefox/3.6.3 "
			+ "(.NET CLR 3.5.30729)";

	/** Used encoding. */
	private static final String ENCODING = "ISO-8859-15";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_fishtext_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(// .
				context.getString(R.string.connector_fishtext_author));
		c.setBalance(null);
		c.setCapabilities(ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND
				| ConnectorSpec.CAPABILITIES_PREFS);
		c.addSubConnector("fishtext", c.getName(),
				SubConnectorSpec.FEATURE_NONE);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
			if (p.getString(Preferences.PREFS_PASSWORD, "").length() > 0) {
				connectorSpec.setReady();
			} else {
				connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
			}
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		return connectorSpec;
	}

	/**
	 * Login to fishtext.com.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @throws IOException
	 *             IOException
	 */
	private void doLogin(final Context context, final ConnectorCommand command)
			throws IOException {
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);

		ArrayList<BasicNameValuePair> postData = // .
		new ArrayList<BasicNameValuePair>(NUM_VARS_LOGIN);
		postData.add(new BasicNameValuePair("action", "login"));
		postData.add(new BasicNameValuePair("mobile", Utils.getSender(context,
				command.getDefSender())));
		postData.add(new BasicNameValuePair("password", p.getString(
				Preferences.PREFS_PASSWORD, "")));
		postData.add(new BasicNameValuePair("rememberSession", "yes"));

		HttpResponse response = Utils.getHttpClient(URL_LOGIN, null, postData,
				TARGET_AGENT, null, ENCODING, false);
		postData = null;
		int resp = response.getStatusLine().getStatusCode();
		if (resp != HttpURLConnection.HTTP_OK) {
			throw new WebSMSException(context, R.string.error_http, "" + resp);
		}
		final String htmlText = Utils.stream2str(response.getEntity()
				.getContent(), STRIP_LOGIN_START, STRIP_LOGIN_END);
		Log.d(TAG, "----HTTP RESPONSE---");
		Log.d(TAG, htmlText);
		Log.d(TAG, "----HTTP RESPONSE---");

		final int i = htmlText.indexOf(CHECK_BALANCE);
		if (i < 0) {
			Utils.clearCookies();
			throw new WebSMSException(context, R.string.error_pw);
		}
		final int j = htmlText.indexOf("<p>", i);
		if (j > 0) {
			final int h = htmlText.indexOf("</p>", j);
			if (h > 0) {
				String b = htmlText.substring(j + 3, h);
				if (b.startsWith("&euro;")) {
					b = b.substring(6) + "\u20AC";
				}
				Log.d(TAG, "balance: " + b);
				this.getSpec(context).setBalance(b);
			}
		}
	}

	/**
	 * Prepare send.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @param throwOnFail
	 *            throw exception on fail, if false a fresh session is loaded
	 *            for retry
	 * @return messageID
	 * @throws IOException
	 *             IOException
	 */
	private String getMessageID(final Context context,
			final ConnectorCommand command, final boolean throwOnFail)
			throws IOException {
		if (Utils.getCookieCount() == 0) {
			this.doLogin(context, command);
		}
		final HttpResponse response = Utils.getHttpClient(URL_PRESEND, null,
				new ArrayList<BasicNameValuePair>(0), TARGET_AGENT, URL_LOGIN,
				ENCODING, false);
		final int resp = response.getStatusLine().getStatusCode();
		if (resp != HttpURLConnection.HTTP_OK) {
			throw new WebSMSException(context, R.string.error_http, "" + resp);
		}
		String htmlText = Utils.stream2str(response.getEntity().getContent());
		// , STRIP_PRESEND_START, STRIP_PRESEND_END);
		Log.d(TAG, "----HTTP RESPONSE---");
		Log.d(TAG, htmlText);
		Log.d(TAG, "----HTTP RESPONSE---");
		final int i = htmlText.indexOf(CHECK_PRESEND);
		if (i < 0) {
			if (throwOnFail) {
				Utils.clearCookies();
				Log.e(TAG, "presend check failed, response following:");
				Log.e(TAG, htmlText);
				throw new WebSMSException(context, R.string.error_service);
			} else {
				// try again with fresh session
				this.doLogin(context, command);
				return this.getMessageID(context, command, true);
			}
		}
		int j = htmlText.indexOf("name=", i);
		if (j < 0) {
			Log.e(TAG, "did not found \"name=\", response following:");
			Log.e(TAG, htmlText);
			throw new WebSMSException(context, R.string.error_service);
		}
		Log.d(TAG, "j = " + j);
		htmlText = htmlText.substring(j + 6);
		j = htmlText.indexOf("\"", 1);
		if (j < 0) {
			Log.e(TAG, "did not found \'\"\', response following:");
			Log.e(TAG, htmlText);
			throw new WebSMSException(context, R.string.error_service);
		}
		final String messageID = htmlText.substring(0, j);
		htmlText = null;
		Log.d(TAG, "message id: " + messageID);
		return messageID;
	}

	/**
	 * Send text.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @throws IOException
	 *             IOException
	 */
	private void sendText(final Context context, final ConnectorCommand command)
			throws IOException {
		ArrayList<BasicNameValuePair> postData = // .
		new ArrayList<BasicNameValuePair>(NUM_VARS_SEND);
		postData.add(new BasicNameValuePair("action", "Send"));
		postData.add(new BasicNameValuePair("SA", "0"));
		postData.add(new BasicNameValuePair("DR", "1"));
		postData.add(new BasicNameValuePair("ST", "1"));
		postData.add(new BasicNameValuePair("RN", Utils.national2international(
				command.getDefPrefix(),
				Utils.getRecipientsNumber(command.getRecipients()[0]))
				.substring(1)));
		postData.add(new BasicNameValuePair(this.getMessageID(context, command,
				false), command.getText()));

		HttpResponse response = Utils.getHttpClient(URL_SEND, null, postData,
				TARGET_AGENT, URL_LOGIN, ENCODING, false);
		postData = null;
		final int resp = response.getStatusLine().getStatusCode();
		if (resp != HttpURLConnection.HTTP_OK) {
			throw new WebSMSException(context, R.string.error_http, "" + resp);
		}
		String htmlText = Utils.stream2str(response.getEntity().getContent());
		Log.d(TAG, "----HTTP RESPONSE---");
		Log.d(TAG, htmlText);
		Log.d(TAG, "----HTTP RESPONSE---");

		final int i = htmlText.indexOf(CHECK_SENT);
		if (i < 0) {
			Log.e(TAG, "failed to send message, response following:");
			Log.e(TAG, htmlText);
			throw new WebSMSException(context, R.string.error_service);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent) {
		try {
			this.doLogin(context, new ConnectorCommand(intent));
		} catch (IOException e) {
			Log.e(TAG, "login failed", e);
			throw new WebSMSException(e.toString());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent) {
		try {
			this.sendText(context, new ConnectorCommand(intent));
		} catch (IOException e) {
			Log.e(TAG, "send failed", e);
			throw new WebSMSException(e.toString());
		}
	}
}
