package net.osmand.plus.wikivoyage.explore;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.BottomNavigationView.OnNavigationItemSelectedListener;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.osmand.AndroidUtils;
import net.osmand.PicassoUtils;
import net.osmand.plus.LockableViewPager;
import net.osmand.plus.R;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.wikivoyage.WikiBaseDialogFragment;
import net.osmand.plus.wikivoyage.data.TravelDbHelper;
import net.osmand.plus.wikivoyage.search.WikivoyageSearchDialogFragment;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;


public class WikivoyageExploreDialogFragment extends WikiBaseDialogFragment implements DownloadIndexesThread.DownloadEvents {

	public static final String TAG = "WikivoyageExploreDialogFragment";

	private static final int EXPLORE_POSITION = 0;
	private static final int SAVED_ARTICLES_POSITION = 1;

	private ExploreTabFragment exploreTabFragment;
	private SavedArticlesTabFragment savedArticlesTabFragment;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Context context = getContext();
		if (context != null) {
			PicassoUtils.setupPicasso(context);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		FragmentManager childFm = getChildFragmentManager();
		List<Fragment> fragments = childFm.getFragments();
		if (fragments != null) {
			for (Fragment fragment : fragments) {
				if (fragment instanceof ExploreTabFragment) {
					exploreTabFragment = (ExploreTabFragment) fragment;
				} else if (fragment instanceof SavedArticlesTabFragment) {
					savedArticlesTabFragment = (SavedArticlesTabFragment) fragment;
				}
			}
		}
		if (exploreTabFragment == null) {
			exploreTabFragment = new ExploreTabFragment();
		}
		if (savedArticlesTabFragment == null) {
			savedArticlesTabFragment = new SavedArticlesTabFragment();
		}

		View mainView = inflate(R.layout.fragment_wikivoyage_explore_dialog, container);

		setupToolbar((Toolbar) mainView.findViewById(R.id.toolbar));

		mainView.findViewById(R.id.options_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				FragmentManager fm = getFragmentManager();
				if (fm == null) {
					return;
				}
				WikivoyageOptionsBottomSheetDialogFragment fragment = new WikivoyageOptionsBottomSheetDialogFragment();
				fragment.setUsedOnMap(false);
				fragment.setTargetFragment(WikivoyageExploreDialogFragment.this,
						WikivoyageOptionsBottomSheetDialogFragment.REQUEST_CODE);
				fragment.show(fm, WikivoyageOptionsBottomSheetDialogFragment.TAG);
			}
		});

		int searchColorId = nightMode ? R.color.icon_color : R.color.ctx_menu_title_color_dark;
		((TextView) mainView.findViewById(R.id.search_hint)).setTextColor(getResolvedColor(searchColorId));
		((ImageView) mainView.findViewById(R.id.search_icon))
				.setImageDrawable(getIcon(R.drawable.ic_action_search_dark, searchColorId));

		mainView.findViewById(R.id.search_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				WikivoyageSearchDialogFragment.showInstance(getFragmentManager());
			}
		});

		final LockableViewPager viewPager = (LockableViewPager) mainView.findViewById(R.id.view_pager);
		viewPager.setOffscreenPageLimit(2);
		viewPager.setSwipeLocked(true);
		viewPager.setAdapter(new ViewPagerAdapter(childFm));

		final ColorStateList navColorStateList = createBottomNavColorStateList();
		final BottomNavigationView bottomNav = (BottomNavigationView) mainView.findViewById(R.id.bottom_navigation);
		bottomNav.setItemIconTintList(navColorStateList);
		bottomNav.setItemTextColor(navColorStateList);
		bottomNav.setOnNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {
			@Override
			public boolean onNavigationItemSelected(@NonNull MenuItem item) {
				int position = -1;
				switch (item.getItemId()) {
					case R.id.action_explore:
						position = EXPLORE_POSITION;
						break;
					case R.id.action_saved_articles:
						position = SAVED_ARTICLES_POSITION;
						break;
				}
				if (position != -1 && position != viewPager.getCurrentItem()) {
					viewPager.setCurrentItem(position);
					return true;
				}
				return false;
			}
		});

		return mainView;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		updateSearchBarVisibility();
		populateData();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		PicassoUtils.clearCachedMap();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == WikivoyageOptionsBottomSheetDialogFragment.REQUEST_CODE) {
			switch (resultCode) {
				case WikivoyageOptionsBottomSheetDialogFragment.DOWNLOAD_IMAGES_CHANGED:
				case WikivoyageOptionsBottomSheetDialogFragment.CACHE_CLEARED:
					invalidateTabAdapters();
					break;
				case WikivoyageOptionsBottomSheetDialogFragment.TRAVEL_BOOK_CHANGED:
					populateData();
					break;
			}
		}
	}

	@Override
	public void newDownloadIndexes() {
		if (exploreTabFragment != null) {
			exploreTabFragment.newDownloadIndexes();
		}
	}

	@Override
	public void downloadInProgress() {
		if (exploreTabFragment != null) {
			exploreTabFragment.downloadInProgress();
		}
	}

	@Override
	public void downloadHasFinished() {
		if (exploreTabFragment != null) {
			exploreTabFragment.downloadHasFinished();
		}
	}

	public void populateData() {
		switchProgressBarVisibility(true);
		new LoadWikivoyageData(this).execute();
	}

	private void onDataLoaded() {
		switchProgressBarVisibility(false);
		updateSearchBarVisibility();
		if (exploreTabFragment != null) {
			exploreTabFragment.populateData();
		}
		if (savedArticlesTabFragment != null) {
			savedArticlesTabFragment.savedArticlesUpdated();
		}
	}

	private void updateSearchBarVisibility() {
		View view = getView();
		if (view != null) {
			boolean show = getMyApplication().getTravelDbHelper().getSelectedTravelBook() != null;
			view.findViewById(R.id.search_box).setVisibility(show ? View.VISIBLE : View.GONE);
		}
	}

	private void switchProgressBarVisibility(boolean show) {
		View view = getView();
		if (view != null) {
			view.findViewById(R.id.progress_bar).setVisibility(show ? View.VISIBLE : View.GONE);
		}
	}

	private void invalidateTabAdapters() {
		if (exploreTabFragment != null) {
			exploreTabFragment.invalidateAdapter();
		}
		if (savedArticlesTabFragment != null) {
			savedArticlesTabFragment.invalidateAdapter();
		}
	}

	private ColorStateList createBottomNavColorStateList() {
		return AndroidUtils.createCheckedColorStateList(getContext(), nightMode,
				R.color.icon_color, R.color.wikivoyage_active_light,
				R.color.icon_color, R.color.wikivoyage_active_dark);
	}

	public static boolean showInstance(FragmentManager fm) {
		try {
			WikivoyageExploreDialogFragment fragment = new WikivoyageExploreDialogFragment();
			fragment.show(fm, TAG);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static class LoadWikivoyageData extends AsyncTask<Void, Void, Void> {

		private WeakReference<WikivoyageExploreDialogFragment> weakReference;
		private TravelDbHelper travelDbHelper;

		LoadWikivoyageData(WikivoyageExploreDialogFragment fragment) {
			travelDbHelper = fragment.getMyApplication().getTravelDbHelper();
			weakReference = new WeakReference<>(fragment);
		}

		@Override
		protected Void doInBackground(Void... params) {
			travelDbHelper.loadDataForSelectedTravelBook();
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			WikivoyageExploreDialogFragment fragment = weakReference.get();
			if (fragment != null && fragment.isResumed()) {
				fragment.onDataLoaded();
			}
		}
	}

	private class ViewPagerAdapter extends FragmentPagerAdapter {

		private final List<BaseOsmAndFragment> fragments = Arrays.asList(exploreTabFragment, savedArticlesTabFragment);

		ViewPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			return fragments.get(position);
		}

		@Override
		public int getCount() {
			return fragments.size();
		}
	}
}
