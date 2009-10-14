/*-
 *  Copyright (C) 2009 Peter Baldwin   
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.peterbaldwin.client.android.tinyurl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class CreateTinyUrlActivity extends Activity implements
		View.OnClickListener, DialogInterface.OnClickListener {

	private EditText mEditUrl;
	private Button mButtonCreate;
	private Button mButtonCancel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.title_create);
		setContentView(R.layout.create);

		mEditUrl = (EditText) findViewById(R.id.edit_url);
		mButtonCreate = (Button) findViewById(R.id.button_create);
		mButtonCancel = (Button) findViewById(R.id.button_cancel);

		mButtonCreate.setOnClickListener(this);
		mButtonCancel.setOnClickListener(this);
	}

	private void handleInvalidUrl() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.message_invalid_url);
		builder.setPositiveButton(R.string.button_ok, this);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void create() {
		Editable text = mEditUrl.getText();
		String url = String.valueOf(text);

		if (Util.isValidUrl(url)) {
			Context packageContext = this;
			Intent intent = new Intent(packageContext,
					SendTinyUrlActivity.class);
			intent.putExtra(Intent.EXTRA_TEXT, url);
			startActivity(intent);
			finish();
		} else {
			handleInvalidUrl();
		}
	}

	private void cancel() {
		finish();
	}

	/**
	 * {@inheritDoc}
	 */
	public void onClick(View v) {
		if (v == mButtonCreate) {
			create();
		} else if (v == mButtonCancel) {
			cancel();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void onClick(DialogInterface dialog, int which) {
		// Keep the activity open so that the user can enter a valid URL.
	}
}
