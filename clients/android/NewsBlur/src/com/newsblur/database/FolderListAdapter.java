package com.newsblur.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Handler;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.newsblur.R;
import com.newsblur.activity.AllSharedStoriesItemsList;
import com.newsblur.activity.AllStoriesItemsList;
import com.newsblur.activity.FolderItemsList;
import com.newsblur.activity.NewsBlurApplication;
import static com.newsblur.database.DatabaseConstants.getStr;
import com.newsblur.domain.Feed;
import com.newsblur.domain.SocialFeed;
import com.newsblur.util.AppConstants;
import com.newsblur.util.ImageLoader;
import com.newsblur.util.StateFilter;
import com.newsblur.view.FolderTreeViewBinder;

/**
 * Custom adapter to display a nested folder/feed list in an ExpandableListView.
 */
public class FolderListAdapter extends BaseExpandableListAdapter {

    private enum GroupType { ALL_SHARED_STORIES, ALL_STORIES, FOLDER, SAVED_STORIES }
    private enum ChildType { SOCIAL_FEED, FEED }

    private Cursor socialFeedCursor;
	private Cursor feedCursor;

    private Map<String,List<String>> folderFeedMap;
    private List<String> activeFolderNames;
    private List<List<String>> activeFolderChildren;
    private List<Integer> neutCounts;
    private List<Integer> posCounts;
    private int savedStoriesCount;

	private Context context;

    private Map<Integer,Integer> folderColumnMap;
    private Map<Integer,Integer> feedColumnMap;
    private Map<Integer,Integer> socialFeedColumnMap;

	private LayoutInflater inflater;
	private ViewBinder binder;

	private StateFilter currentState = StateFilter.SOME;

	public FolderListAdapter(Context context) {
		this.context = context;
		ImageLoader imageLoader = ((NewsBlurApplication) context.getApplicationContext()).getImageLoader();
		this.binder = new FolderTreeViewBinder(imageLoader);
		this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
		View v = convertView;
		if (groupPosition == 0) {
			v =  inflater.inflate(R.layout.row_all_shared_stories, null, false);
			((TextView) v.findViewById(R.id.row_everythingtext)).setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(context, AllSharedStoriesItemsList.class);
					i.putExtra(AllStoriesItemsList.EXTRA_STATE, currentState);
					((Activity) context).startActivityForResult(i, Activity.RESULT_OK);
				}
			});
            if (socialFeedCursor != null) {
                int neutCount = sumIntRows(socialFeedCursor, socialFeedCursor.getColumnIndex(DatabaseConstants.SOCIAL_FEED_NEUTRAL_COUNT));
                neutCount = checkNegativeUnreads(neutCount);
                if (currentState == StateFilter.BEST || (neutCount == 0)) {
                    v.findViewById(R.id.row_foldersumneu).setVisibility(View.GONE);
                } else {
                    v.findViewById(R.id.row_foldersumneu).setVisibility(View.VISIBLE);
                    ((TextView) v.findViewById(R.id.row_foldersumneu)).setText(Integer.toString(neutCount));	
                }
                int posCount = sumIntRows(socialFeedCursor, socialFeedCursor.getColumnIndex(DatabaseConstants.SOCIAL_FEED_POSITIVE_COUNT));
                posCount = checkNegativeUnreads(posCount);
                if (posCount == 0) {
                    v.findViewById(R.id.row_foldersumpos).setVisibility(View.GONE);
                } else {
                    v.findViewById(R.id.row_foldersumpos).setVisibility(View.VISIBLE);
                    ((TextView) v.findViewById(R.id.row_foldersumpos)).setText(Integer.toString(posCount));
                }
            } 
            v.findViewById(R.id.row_foldersums).setVisibility(isExpanded ? View.INVISIBLE : View.VISIBLE);
			((ImageView) v.findViewById(R.id.row_folder_indicator)).setImageResource(isExpanded ? R.drawable.indicator_expanded : R.drawable.indicator_collapsed);
		} else if (isFolderRoot(groupPosition)) {
			v =  inflater.inflate(R.layout.row_all_stories, null, false);
            int posCount = 0;
            for (int i : posCounts) posCount += i;
            posCount = checkNegativeUnreads(posCount);
            int neutCount = 0;
            for (int i : neutCounts) neutCount += i;
            neutCount = checkNegativeUnreads(neutCount);
			switch (currentState) {
				case BEST:
					v.findViewById(R.id.row_foldersumneu).setVisibility(View.GONE);
					v.findViewById(R.id.row_foldersumpos).setVisibility(View.VISIBLE);
					((TextView) v.findViewById(R.id.row_foldersumpos)).setText(Integer.toString(posCount));
					break;
				default:
					v.findViewById(R.id.row_foldersumneu).setVisibility(View.VISIBLE);
                    if (posCount == 0) {
                        v.findViewById(R.id.row_foldersumpos).setVisibility(View.GONE);
                    } else {
                        v.findViewById(R.id.row_foldersumpos).setVisibility(View.VISIBLE);
                    }
					((TextView) v.findViewById(R.id.row_foldersumneu)).setText(Integer.toString(neutCount));
					((TextView) v.findViewById(R.id.row_foldersumpos)).setText(Integer.toString(posCount));
					break;
			}
        } else if (isRowSavedStories(groupPosition)) {
            if (convertView == null) {
                v = inflater.inflate(R.layout.row_saved_stories, null, false);
            }
            ((TextView) v.findViewById(R.id.row_foldersum)).setText(Integer.toString(savedStoriesCount));
		} else {
			if (convertView == null) {
				v = inflater.inflate((isExpanded) ? R.layout.row_folder_collapsed : R.layout.row_folder_collapsed, parent, false);
			}
            final String folderName = activeFolderNames.get(groupPosition-1);
			TextView folderTitle = ((TextView) v.findViewById(R.id.row_foldername));
		    folderTitle.setText(folderName.toUpperCase());
			folderTitle.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(v.getContext(), FolderItemsList.class);
					i.putExtra(FolderItemsList.EXTRA_FOLDER_NAME, folderName);
					i.putExtra(FolderItemsList.EXTRA_STATE, currentState);
					((Activity) context).startActivity(i);
				}
			});
            // TODO: bind counts
            v.findViewById(R.id.row_foldersums).setVisibility(isExpanded ? View.INVISIBLE : View.VISIBLE);
            ImageView folderIconView = ((ImageView) v.findViewById(R.id.row_folder_icon));
            if ( folderIconView != null ) {
                folderIconView.setImageResource(isExpanded ? R.drawable.g_icn_folder : R.drawable.g_icn_folder_rss);
            }
            ImageView folderIndicatorView = ((ImageView) v.findViewById(R.id.row_folder_indicator));
            if ( folderIndicatorView != null ) {
                folderIndicatorView.setImageResource(isExpanded ? R.drawable.indicator_expanded : R.drawable.indicator_collapsed);
            }
		}
		return v;
	}

	@Override
	public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
		View v;
		if (groupPosition == 0) {
			socialFeedCursor.moveToPosition(childPosition);
			if (convertView == null) {
                v = inflater.inflate(R.layout.row_socialfeed, parent, false);
			} else {
				v = convertView;
			}
            bindView(v, socialFeedCursor, this.socialFeedColumnMap);
		} else {
            moveFeedCursorToId(activeFolderChildren.get(groupPosition-1).get(childPosition));
			if (convertView == null) {
				v = inflater.inflate(R.layout.row_feed, parent, false);
			} else {
				v = convertView;
			}
            bindView(v, feedCursor, this.feedColumnMap);
		}
		return v;
	}

	@Override
	public String getGroup(int groupPosition) {
		return activeFolderNames.get(groupPosition - 1);
	}

	@Override
	public int getGroupCount() {
        // in addition to the real folders returned by the /reader/feeds API, there are virtual folders
        // for social feeds and saved stories
        if (activeFolderNames == null) return 0;
		return (activeFolderNames.size() + 2);
	}

	@Override
	public long getGroupId(int groupPosition) {
		if (groupPosition == 0) {
            // the social folder doesn't have an ID, so just give it a really huge one
            return Long.MAX_VALUE;
        } else if (isRowSavedStories(groupPosition)) {
            // neither does the saved stories row, give it another
            return (Long.MAX_VALUE-1);
        } else {
		    return activeFolderNames.get(groupPosition-1).hashCode();
		}
	}
	
	@Override
	public int getChildrenCount(int groupPosition) {
		if (groupPosition == 0) {
            if (socialFeedCursor == null) return 0;
			return socialFeedCursor.getCount();
        } else if (isRowSavedStories(groupPosition)) {
            return 0; // this row never has children
		} else {
            return activeFolderChildren.get(groupPosition-1).size();
		}
	}

	@Override
	public String getChild(int groupPosition, int childPosition) {
		if (groupPosition == 0) {
			socialFeedCursor.moveToPosition(childPosition);
			return getStr(socialFeedCursor, DatabaseConstants.SOCIAL_FEED_ID);
        } else {
			return activeFolderChildren.get(groupPosition-1).get(childPosition);
		}
	}

	@Override
    public long getChildId(int groupPosition, int childPosition) {
		return getChild(groupPosition, childPosition).hashCode();
	}

	public String getGroupName(int groupPosition) {
        // these "names" aren't actually what is used to render the row, but are used
        // by the fragment for tracking row identity to save open/close preferences
		if (groupPosition == 0) {
			return "[ALL_SHARED_STORIES]";
		} else if (isRowSavedStories(groupPosition)) {
            return "[SAVED_STORIES]";
        } else {
			return activeFolderNames.get(groupPosition-1);
		}
	}

    /**
     * Determines if the folder at the specified position is the special "root" folder.  This
     * folder is returned by the API in a special way and the APIManager ensures it gets a
     * specific name in the DB so we can find it.
     */
    public boolean isFolderRoot(int groupPosition) {
        return ( getGroupName(groupPosition).equals(AppConstants.ROOT_FOLDER) );
    }

    /**
     * Determines if the row at the specified position is the special "saved" folder. This
     * row doesn't actually correspond to a row in the DB, much like the social row, but
     * it is located at the bottom of the set rather than the top.
     */
    public boolean isRowSavedStories(int groupPosition) {
        return ( groupPosition > activeFolderNames.size() );
    }

	public void setSocialFeedCursor(Cursor cursor) {
		this.socialFeedCursor = cursor;
        if (socialFeedColumnMap == null) {
            socialFeedColumnMap = new HashMap<Integer,Integer>();
            socialFeedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.SOCIAL_FEED_TITLE), R.id.row_socialfeed_name);
            socialFeedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.SOCIAL_FEED_ICON), R.id.row_socialfeed_icon);
            socialFeedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.SOCIAL_FEED_NEUTRAL_COUNT), R.id.row_socialsumneu);
            socialFeedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.SOCIAL_FEED_POSITIVE_COUNT), R.id.row_socialsumpos);
        }
        notifyDataSetChanged();
	}

    public synchronized void setFolderFeedMapCursor(Cursor cursor) {
        if (cursor.getCount() < 1) return;
        this.folderFeedMap = new TreeMap<String,List<String>>();
        // some newer frameworks like to re-use cursors, so we cannot assume a starting index
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String folderName = getStr(cursor, DatabaseConstants.FEED_FOLDER_FOLDER_NAME);
            String feedId = getStr(cursor, DatabaseConstants.FEED_FOLDER_FEED_ID);
            if (! folderFeedMap.containsKey(folderName)) folderFeedMap.put(folderName, new ArrayList<String>());
            folderFeedMap.get(folderName).add(feedId);
        }
        recountFeeds();
        notifyDataSetChanged();
    }

	public synchronized void setFeedCursor(Cursor cursor) {
		this.feedCursor = cursor;
        if (feedColumnMap == null) {
            feedColumnMap = new HashMap<Integer,Integer>();
            feedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.FEED_TITLE), R.id.row_feedname);
            feedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.FEED_FAVICON_URL), R.id.row_feedfavicon);
            feedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.FEED_NEUTRAL_COUNT), R.id.row_feedneutral);
            feedColumnMap.put(cursor.getColumnIndexOrThrow(DatabaseConstants.FEED_POSITIVE_COUNT), R.id.row_feedpositive);
        }
        recountFeeds();
        notifyDataSetChanged();
	}

	public void setSavedCountCursor(Cursor cursor) {
        cursor.moveToFirst();
        if (cursor.getCount() > 0) {
            savedStoriesCount = cursor.getInt(cursor.getColumnIndex(DatabaseConstants.STARRED_STORY_COUNT_COUNT));
        }
        notifyDataSetChanged();
	}
    
    private void recountFeeds() {
        if (feedCursor == null || folderFeedMap == null) return;
        int c = folderFeedMap.keySet().size();
        activeFolderNames = new ArrayList<String>(c);
        activeFolderChildren = new ArrayList<List<String>>(c);
        neutCounts = new ArrayList<Integer>(c);
        posCounts = new ArrayList<Integer>(c);
        for (String folderName : folderFeedMap.keySet()) {
            List<String> activeFeeds = new ArrayList<String>();
            int neutCount = 0;
            int posCount = 0;
            for (String feedId : folderFeedMap.get(folderName)) {
                moveFeedCursorToId(feedId);
                if (!feedCursor.isBeforeFirst()) {
                    int feedNeutCount = feedCursor.getInt(feedCursor.getColumnIndex(DatabaseConstants.FEED_NEUTRAL_COUNT));
                    int feedPosCount = feedCursor.getInt(feedCursor.getColumnIndex(DatabaseConstants.FEED_POSITIVE_COUNT));
                    if (((currentState == StateFilter.BEST) && (feedPosCount > 0)) ||
                        ((currentState == StateFilter.SOME) && ((feedPosCount+feedNeutCount > 0))) ||
                        (currentState == StateFilter.ALL)) {
                        activeFeeds.add(feedId);
                    }
                    neutCount += feedNeutCount;
                    posCount += feedPosCount;
                }
            }
            if (activeFeeds.size() > 0) {
                activeFolderNames.add(folderName);
                activeFolderChildren.add(activeFeeds);
                neutCounts.add(neutCount);
                posCounts.add(posCount);
            }
        }
    }

    private void moveFeedCursorToId(String feedId) {
        // start at -1
        feedCursor.moveToPosition(-1);
        while (feedCursor.moveToNext()) {
            if (getStr(feedCursor, DatabaseConstants.FEED_ID).equals(feedId)) return;
        }
        // never got a hit, return to -1
        feedCursor.moveToPosition(-1);
    }

    public Feed getFeed(String feedId) {
        moveFeedCursorToId(feedId);
        return Feed.fromCursor(feedCursor);
    }

    public SocialFeed getSocialFeed(String socialFeedId) {
        socialFeedCursor.moveToPosition(-1);
        while (socialFeedCursor.moveToNext()) {
            if (getStr(socialFeedCursor, DatabaseConstants.SOCIAL_FEED_ID).equals(socialFeedId)) break;
        }
        return SocialFeed.fromCursor(socialFeedCursor);
    }

	public void changeState(StateFilter state) {
		currentState = state;
    }

	@Override
	public boolean isChildSelectable(int groupPosition, int childPosition) {
		return true;
	}

    /*
     * These next five methods are used by the framework to decide which views can
     * be recycled when calling getChildView and getGroupView.
     */

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public int getGroupType(int groupPosition) {
		if (groupPosition == 0) {
			return GroupType.ALL_SHARED_STORIES.ordinal();
		} else if (isFolderRoot(groupPosition)) {
            return GroupType.ALL_STORIES.ordinal();
        } else if (isRowSavedStories(groupPosition)) {
            return GroupType.SAVED_STORIES.ordinal();
        } else {
			return GroupType.FOLDER.ordinal();
		}
	}

    @Override
	public int getChildType(int groupPosition, int childPosition) {
		if (groupPosition == 0) {
			return ChildType.SOCIAL_FEED.ordinal();
		} else {
			return ChildType.FEED.ordinal();
		}
	}

	@Override
	public int getGroupTypeCount() {
		return GroupType.values().length;
	}

	@Override
	public int getChildTypeCount() {
		return ChildType.values().length;
	}

	private void bindView(View view, Cursor cursor, Map<Integer,Integer> columnMap) {
        for (Map.Entry<Integer,Integer> column : columnMap.entrySet()) {
			View v = view.findViewById(column.getValue());
			if (v != null) {
				binder.setViewValue(v, cursor, column.getKey());
			}
		}
	}

    private int sumIntRows(Cursor c, int columnIndex) {
        if (c == null) return 0;
        int i = 0;
        c.moveToPosition(-1);
        while (c.moveToNext()) {
            i += c.getInt(columnIndex);
        }
        return i;
    }

    /**
     * Utility method to filter out and carp about negative unread counts.  These tend to indicate
     * a problem in the app or API, but are very confusing to users.
     */
    private int checkNegativeUnreads(int count) {
        if (count < 0) {
            Log.w(this.getClass().getName(), "Negative unread count found and rounded up to zero.");
            return 0;
        }
        return count;
    }
}
