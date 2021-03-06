package net.osmand.plus.wikipedia;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import net.osmand.AndroidUtils;
import net.osmand.IndexConstants;
import net.osmand.data.Amenity;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.development.OsmandDevelopmentPlugin;
import net.osmand.plus.helpers.FileNameTranslationHelper;
import net.osmand.util.Algorithms;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


public class WikipediaDialogFragment extends WikiArticleBaseDialogFragment {

	public static final String TAG = "WikipediaDialogFragment";

	private TextView readFullArticleButton;

	private Amenity amenity;
	private String lang;
	private String title;
	private String article;

	public void setAmenity(Amenity amenity) {
		this.amenity = amenity;
	}

	public void setLanguage(String lang) {
		this.lang = lang;
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View mainView = inflater.inflate(R.layout.wikipedia_dialog_fragment, container, false);

		setupToolbar((Toolbar) mainView.findViewById(R.id.toolbar));

		articleToolbarText = (TextView) mainView.findViewById(R.id.title_text_view);
		ImageView options = (ImageView) mainView.findViewById(R.id.options_button);
		options.setImageDrawable(getIcon(R.drawable.ic_overflow_menu_white, R.color.icon_color));
		options.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				OsmandApplication app = getMyApplication();
				if (app != null) {
					FragmentManager fm = getFragmentManager();
					if (fm == null) {
						return;
					}
					WikipediaOptionsBottomSheetDialogFragment fragment = new WikipediaOptionsBottomSheetDialogFragment();
					fragment.setUsedOnMap(false);
					fragment.setTargetFragment(WikipediaDialogFragment.this,
							WikipediaOptionsBottomSheetDialogFragment.REQUEST_CODE);
					fragment.show(fm, WikipediaOptionsBottomSheetDialogFragment.TAG);
				}
			}
		});
		ColorStateList buttonColorStateList = AndroidUtils.createPressedColorStateList(getContext(), nightMode,
				R.color.ctx_menu_controller_button_text_color_light_n, R.color.ctx_menu_controller_button_text_color_light_p,
				R.color.ctx_menu_controller_button_text_color_dark_n, R.color.ctx_menu_controller_button_text_color_dark_p);

		ColorStateList selectedLangColorStateList = AndroidUtils.createPressedColorStateList(
				getContext(), nightMode,
				R.color.icon_color, R.color.wikivoyage_active_light,
				R.color.icon_color, R.color.wikivoyage_active_dark
		);

		readFullArticleButton = (TextView) mainView.findViewById(R.id.read_full_article);
		readFullArticleButton.setBackgroundResource(nightMode ? R.drawable.bt_round_long_night : R.drawable.bt_round_long_day);
		readFullArticleButton.setTextColor(buttonColorStateList);
		readFullArticleButton.setCompoundDrawablesWithIntrinsicBounds(getIcon(R.drawable.ic_world_globe_dark), null, null, null);
		readFullArticleButton.setCompoundDrawablePadding((int) getResources().getDimension(R.dimen.content_padding_small));
		int paddingLeft = (int) getResources().getDimension(R.dimen.wikipedia_button_left_padding);
		int paddingRight = (int) getResources().getDimension(R.dimen.dialog_content_margin);
		readFullArticleButton.setPadding(paddingLeft, 0, paddingRight, 0);

		selectedLangTv = (TextView) mainView.findViewById(R.id.select_language_text_view);
		selectedLangTv.setTextColor(selectedLangColorStateList);
		selectedLangTv.setCompoundDrawablesWithIntrinsicBounds(getSelectedLangIcon(), null, null, null);
		selectedLangTv.setBackgroundResource(nightMode
				? R.drawable.wikipedia_select_lang_bg_dark_n : R.drawable.wikipedia_select_lang_bg_light_n);

		contentWebView = (WebView) mainView.findViewById(R.id.content_web_view);
		WebSettings webSettings = contentWebView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		contentWebView.setWebViewClient(new WikipediaWebViewClient(getActivity(), nightMode));
		updateWebSettings();
		contentWebView.setBackgroundColor(ContextCompat.getColor(getMyApplication(),
				nightMode ? R.color.wiki_webview_background_dark : R.color.wiki_webview_background_light));

		return mainView;
	}

	@Override
	@NonNull
	protected String createHtmlContent() {
		StringBuilder sb = new StringBuilder(HEADER_INNER);
		String nightModeClass = nightMode ? " nightmode" : "";
		sb.append("<div class=\"main");
		sb.append(nightModeClass);
		sb.append("\">\n");
		sb.append("<h1>").append(title).append("</h1>");
		sb.append(article);
		sb.append(FOOTER_INNER);
		if (OsmandPlugin.getEnabledPlugin(OsmandDevelopmentPlugin.class) != null) {
			writeOutHTML(sb, new File(getMyApplication().getAppPath(IndexConstants.WIKIVOYAGE_INDEX_DIR), "page.html"));
		}
		return sb.toString();
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		populateArticle();
	}

	@Override
	public void onDestroyView() {
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.setDismissMessage(null);
		}
		super.onDestroyView();
	}

	@Override
	protected void populateArticle() {
		if (amenity != null) {
			String preferredLanguage = lang;
			if (TextUtils.isEmpty(preferredLanguage)) {
				preferredLanguage = getMyApplication().getLanguage();
			}

			String lng = amenity.getContentLanguage("content", preferredLanguage, "en");
			if (Algorithms.isEmpty(lng)) {
				lng = "en";
			}

			final String langSelected = lng;
			article = amenity.getDescription(langSelected);
			title = amenity.getName(langSelected);
			articleToolbarText.setText(title);
			readFullArticleButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					String article = "https://" + langSelected.toLowerCase() + ".wikipedia.org/wiki/" + title.replace(' ', '_');
					showFullArticle(getContext(), Uri.parse(article), nightMode);
				}
			});

			selectedLangTv.setText(Algorithms.capitalizeFirstLetter(langSelected));
			selectedLangTv.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					showPopupLangMenu(selectedLangTv, langSelected);
				}
			});
			contentWebView.loadDataWithBaseURL(getBaseUrl(), createHtmlContent(), "text/html", "UTF-8", null);
		}
	}

	public static void showFullArticle(Context context, Uri uri, boolean nightMode) {
		CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
				.setToolbarColor(ContextCompat.getColor(context, nightMode ? R.color.actionbar_dark_color : R.color.actionbar_light_color))
				.build();
		customTabsIntent.launchUrl(context, uri);
	}

	@Override
	protected void showPopupLangMenu(View view, final String langSelected) {
		final PopupMenu optionsMenu = new PopupMenu(getContext(), view, Gravity.RIGHT);
		Set<String> namesSet = new TreeSet<>();
		namesSet.addAll(amenity.getNames("content", "en"));
		namesSet.addAll(amenity.getNames("description", "en"));

		Map<String, String> names = new HashMap<>();
		for (String n : namesSet) {
			names.put(n, FileNameTranslationHelper.getVoiceName(getContext(), n));
		}
		String selectedLangName = names.get(langSelected);
		if (selectedLangName != null) {
			names.remove(langSelected);
		}
		Map<String, String> sortedNames = AndroidUtils.sortByValue(names);

		if (selectedLangName != null) {
			MenuItem item = optionsMenu.getMenu().add(selectedLangName);
			item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					setLanguage(langSelected);
					populateArticle();
					return true;
				}
			});
		}
		for (final Map.Entry<String, String> e : sortedNames.entrySet()) {
			MenuItem item = optionsMenu.getMenu().add(e.getValue());
			item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					setLanguage(e.getKey());
					populateArticle();
					return true;
				}
			});
		}
		optionsMenu.show();
	}

	private Drawable getIcon(int resId) {
		int colorId = nightMode ? R.color.ctx_menu_controller_button_text_color_dark_n : R.color.ctx_menu_controller_button_text_color_light_n;
		return getIcon(resId, colorId);
	}

	public static boolean showInstance(AppCompatActivity activity, Amenity amenity, String lang) {
		try {
			if (!amenity.getType().isWiki()) {
				return false;
			}
			OsmandApplication app = (OsmandApplication) activity.getApplication();

			WikipediaDialogFragment wikipediaDialogFragment = new WikipediaDialogFragment();
			wikipediaDialogFragment.setAmenity(amenity);
			wikipediaDialogFragment.setLanguage(lang == null ?
					app.getSettings().MAP_PREFERRED_LOCALE.get() : lang);
			wikipediaDialogFragment.setRetainInstance(true);
			wikipediaDialogFragment.show(activity.getSupportFragmentManager(), TAG);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static boolean showInstance(AppCompatActivity activity, Amenity amenity) {
		return showInstance(activity, amenity, null);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == WikipediaOptionsBottomSheetDialogFragment.REQUEST_CODE
				&& resultCode == WikipediaOptionsBottomSheetDialogFragment.SHOW_PICTURES_CHANGED_REQUEST_CODE) {
			updateWebSettings();
			populateArticle();
		}
	}
}
