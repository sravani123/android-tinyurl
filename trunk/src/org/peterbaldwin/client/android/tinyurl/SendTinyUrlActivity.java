package org.peterbaldwin.client.android.tinyurl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URLEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class SendTinyUrlActivity extends Activity implements
		View.OnClickListener, DialogInterface.OnClickListener, Runnable {
	private TextView mTextOriginalUrl;
	private ProgressBar mProgressUrl;
	private TextView mTextUrl;
	private Button mButtonSend;
	private Button mButtonCopy;
	private Button mButtonCancel;

	private String mUrl;
	private String mTinyUrl;

	static final int HANDLE_URL = 1;
	static final int HANDLE_ERROR = 2;

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case HANDLE_URL:
				String url = (String) msg.obj;
				handleUrl(url);
				break;
			case HANDLE_ERROR:
				Throwable t = (Throwable) msg.obj;
				handleError(t);
				break;
			}
		}
	};

	void handleUrl(String url) {
		mTinyUrl = url;

		setTitle(R.string.title_created);
		mProgressUrl.setVisibility(View.GONE);
		mTextUrl.setText(url);
		mTextUrl.setVisibility(View.VISIBLE);
		mButtonSend.setEnabled(true);
		mButtonCopy.setEnabled(true);
	}

	void handleError(Throwable throwable) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (throwable instanceof MalformedURLException) {
			builder.setMessage(R.string.message_invalid_url);
		} else {
			// TODO: User-friendly error messages
			builder.setMessage(String.valueOf(throwable));
		}
		builder.setPositiveButton(R.string.button_ok, this);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.title_creating);
		setContentView(R.layout.send);
		mTextOriginalUrl = (TextView) findViewById(R.id.text_original_url);
		mProgressUrl = (ProgressBar) findViewById(R.id.progress_url);
		mTextUrl = (TextView) findViewById(R.id.text_url);
		mButtonSend = (Button) findViewById(R.id.button_send);
		mButtonCopy = (Button) findViewById(R.id.button_copy);
		mButtonCancel = (Button) findViewById(R.id.button_cancel);

		mButtonSend.setOnClickListener(this);
		mButtonCopy.setOnClickListener(this);
		mButtonCancel.setOnClickListener(this);

		// Disable these buttons until the TinyURL has been created.
		mButtonSend.setEnabled(false);
		mButtonCopy.setEnabled(false);

		Intent intent = getIntent();
		mUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
		if (mUrl == null) {
			// Use a default URL if the activity is launched directly.
			mUrl = "http://www.google.com/";
		}

		mTextOriginalUrl.setText(mUrl);

		// Request a TinyURL on a background thread.
		// This request is fast, so don't worry about the activity being
		// re-created if the keyboard is opened.
		new Thread(this).start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void onClick(DialogInterface dialog, int which) {
		// Close activity after acknowledging error message.
		// It generally won't be difficult for the user to retry the request
		// from the activity the launched the original Intent.
		finish();
	}

	/**
	 * {@inheritDoc}
	 */
	public void onClick(View v) {
		if (v == mButtonSend) {
			send();
		} else if (v == mButtonCopy) {
			copy();
		} else if (v == mButtonCancel) {
			cancel();
		}
	}

	private void send() {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");

		Intent originalIntent = getIntent();
		if (Intent.ACTION_SEND.equals(originalIntent.getAction())) {
			// Copy extras from the original intent because they might contain
			// additional information about the URL (e.g., the title of a
			// YouTube video). Do this before setting Intent.EXTRA_TEXT to avoid
			// overwriting the TinyURL.
			intent.putExtras(originalIntent.getExtras());
		}

		intent.putExtra(Intent.EXTRA_TEXT, mTinyUrl);
		try {
			CharSequence template = getText(R.string.title_send);
			String title = String.format(String.valueOf(template), mTinyUrl);
			startActivity(Intent.createChooser(intent, title));
			finish();
		} catch (ActivityNotFoundException e) {
			handleError(e);
		}
	}

	private void copy() {
		Object service = getSystemService(CLIPBOARD_SERVICE);
		ClipboardManager clipboard = (ClipboardManager) service;
		clipboard.setText(mTinyUrl);

		// Let the user know that the copy was successful.
		int resId = R.string.message_copied;
		Toast toast = Toast.makeText(this, resId, Toast.LENGTH_SHORT);
		toast.show();
		finish();
	}

	private void cancel() {
		finish();
	}

	/**
	 * Sends the TinyURL to the event thread.
	 * 
	 * @param url
	 *            the TinyURL.
	 */
	private void sendUrl(String url) {
		mHandler.obtainMessage(HANDLE_URL, url).sendToTarget();
	}

	/**
	 * Sends an error to the event thread.
	 * 
	 * @param t
	 *            the error.
	 */
	private void sendError(Throwable t) {
		mHandler.obtainMessage(HANDLE_ERROR, t).sendToTarget();
	}

	/**
	 * {@inheritDoc}
	 */
	public void run() {
		if (!Util.isValidUrl(mUrl)) {
			sendError(new MalformedURLException());
			return;
		}
		try {
			HttpClient client = new DefaultHttpClient();
			String urlTemplate = "http://tinyurl.com/api-create.php?url=%s";
			String uri = String.format(urlTemplate, URLEncoder.encode(mUrl));
			HttpGet request = new HttpGet(uri);
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			InputStream in = entity.getContent();
			try {
				StatusLine statusLine = response.getStatusLine();
				int statusCode = statusLine.getStatusCode();
				if (statusCode == HttpStatus.SC_OK) {
					// TODO: Support other encodings
					String enc = "utf-8";
					Reader reader = new InputStreamReader(in, enc);
					BufferedReader bufferedReader = new BufferedReader(reader);
					String tinyUrl = bufferedReader.readLine();
					if (tinyUrl != null) {
						sendUrl(tinyUrl);
					} else {
						throw new IOException("empty response");
					}
				} else {
					String errorTemplate = "unexpected response: %d";
					String msg = String.format(errorTemplate, statusCode);
					throw new IOException(msg);
				}
			} finally {
				in.close();
			}
		} catch (IOException e) {
			sendError(e);
		} catch (RuntimeException e) {
			sendError(e);
		} catch (Error e) {
			sendError(e);
		}
	}
}
