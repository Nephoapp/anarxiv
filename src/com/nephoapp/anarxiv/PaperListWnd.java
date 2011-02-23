/*
 * Copyright (C) 2011 Nephoapp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nephoapp.anarxiv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class PaperListWnd extends Activity implements OnItemClickListener, OnScrollListener
{
	/** ui components. */
	ListView _uiPaperList = null;
	TextView _uiCategoryName = null;
	ProgressDialog _uiBusyBox = null;
	
	/** adapter for paper list. */
	SimpleAdapter _uiPaperListAdapter = null;
	SimpleAdapter _uiPaperListAdapter1 = null;
	
	/** gesture detector. */
	private GestureDetector _gestureDetector = null;
	
	/** sync lock. */
//	private Object _lock = new Object();
	
	/** whether loading thread is running. */
	private boolean _isLoading = false;
	
//	private static boolean _cachedBefore=false;
	
	/** arxiv loader. */
	private ArxivLoader _arxivLoader = ArxivLoader.getInstance();
	
	/** the category of this list for query. */
	private String _paperCategory = null;
	
	/** the descriptive name of the category. */
	private String _paperCategoryName = null;
	/**  the name of parent catgory                        */
	private String _paperParentCat=null;
	/** newly loaded paper list. */
//	private ArrayList<ArxivLoader.Paper> _newPaperList = null;
	
	/** paper map list. */
	private List<Map<String, Object>> _paperMapList = new ArrayList<Map<String, Object>>();
	private List<Map<String, Object>> _paperMapList1 = new ArrayList<Map<String, Object>>();
	/**
	 * gesture handler.
	 */
	private class myGestureListener extends GestureDetector.SimpleOnGestureListener
	{
		/**
		 * onFling.
		 */
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			if (e1.getX() - e2.getX() > ConstantTable.FLING_MIN_DISTANCE && 
					Math.abs(velocityX) > ConstantTable.FLING_MIN_VELOCITY)
			{
				finish();
			}
			
			return super.onFling(e1, e2, velocityX, velocityY);
		}
	}
	
	/**
	 * Loading thread.
	 */
	private class ArxivLoadingThread extends Thread implements DialogInterface.OnKeyListener
	{
		public synchronized void run()
		{
			try
			{	
				/* get data. */
				List<Map<String, Object>> paperMapList = _arxivLoader.loadPapers(_paperCategory);
				_paperMapList.addAll(paperMapList);
				/* write db cachedpaper*/
				try
				{
					AnarxivDB db = AnarxivDB.getInstance();
					
					
					
					for( Map<String, Object> paperitem : paperMapList)
					{
						/* fill out the paper object and add to database. */
						AnarxivDB.Paper paper = new AnarxivDB.Paper();
						paper._author = (String)paperitem.get("author");
						paper._date = (String)paperitem.get("date");
						paper._id = (String)paperitem.get("id");
						paper._title = (String)paperitem.get("title");
						paper._url = (String)paperitem.get("url");
						
						db.addCachedPaperlist(paper,_paperCategory );
						
					}
					
				}
				catch (AnarxivDB.DBException e)
				{
				//	UiUtils.showToast(this, e.getMessage());
				}
				
				
				PaperListWnd.this.runOnUiThread(new Runnable()
						{
							public void run()
							{
								
								//_cachedBefore=true;
								if (_uiPaperListAdapter == null)
								{
									_uiPaperListAdapter = new SimpleAdapter(PaperListWnd.this,
																			_paperMapList,
																			R.layout.paper_list_item,
																			new String[] {"title", 
																						  "date", 
																						  "author"},
																			new int[] {R.id.paperitem_title, 
																					   R.id.paperitem_date, 
																					   R.id.paperitem_author});
									_uiPaperList.setAdapter(_uiPaperListAdapter);
								}
	   						  else
								{
									/* notify the view that the data has changed. */
									_uiPaperListAdapter.notifyDataSetChanged();
								}
								
								if (_uiBusyBox != null)
								{
									_uiBusyBox.dismiss();
									_uiBusyBox = null;
								}
								
								_isLoading = false;
							}
						});
			}
			catch (ArxivLoader.LoaderException e)
			{	
				final ArxivLoader.LoaderException err = e;
				
				PaperListWnd.this.runOnUiThread(new Runnable()
					{
						public void run()
						{
							/* dismiss busy box if any. */
							if (_uiBusyBox != null)
							{
								_uiBusyBox.dismiss();
								_uiBusyBox = null;
							}
							
							_isLoading = false;
							
							/* show error message. */
							UiUtils.showErrorMessage(PaperListWnd.this, err.getMessage());
						}
					});
			}
			catch (Exception e)
			{
				PaperListWnd.this.runOnUiThread(new Runnable()
				{
					public void run()
					{
						/* dismiss busy box if any. */
						if (_uiBusyBox != null)
						{
							_uiBusyBox.dismiss();
							_uiBusyBox = null;
						}
						
						_isLoading = false;
					}
				});
			}
		}
		
		public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) 
		{
			// TODO Auto-generated method stub
			UiUtils.showToast(PaperListWnd.this, "back key pressed");
			return true;
		}
	}
	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.paper_list);
		
		/* reste loader. */
		_arxivLoader.reset();
		
		/* get intent params. */
		Intent intent = getIntent();
		_paperCategory = intent.getStringExtra("category");
		_paperCategoryName = intent.getStringExtra("categoryname");
		_paperParentCat=intent.getStringExtra("parentcat");
		
		/* get ui components. */
		_uiPaperList = (ListView)findViewById(R.id.paperlist);
		_uiCategoryName = (TextView)findViewById(R.id.categoryname);
		
		/* set ui category name. */
		_uiCategoryName.setText(_paperCategoryName);
		
		/* set event handler. */
		_uiPaperList.setOnItemClickListener(this);
		_uiPaperList.setOnScrollListener(this);
		
		/* register context menu. */
		registerForContextMenu(_uiPaperList);
		
		/* gesture detector. */
		_gestureDetector = new GestureDetector(this, new myGestureListener());
		
		String service=Context.CONNECTIVITY_SERVICE;
		ConnectivityManager connectivity=(ConnectivityManager) getSystemService(service);
		NetworkInfo wifiNetwork=connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo mobiNetwork=connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if(wifiNetwork.getState()==NetworkInfo.State.CONNECTED||mobiNetwork.getState()==NetworkInfo.State.CONNECTED)
		{

			/* show busy box. */
			_uiBusyBox = ProgressDialog.show(this, "",
											 getResources().getText(R.string.loading_please_wait));
		ArxivLoadingThread t = new ArxivLoadingThread();
		t.start();
		}
		else{
			UiUtils.showToast(this, "No Network Connection, Show the Cached History");
			loadCachedPaperlist();
			/* show no network  box. */
		//	UiUtils.showErrorMessage(this, getResources().getString(R.string.No_Network));
		}
		
		

		
		
		
	
	}
	
	/**
	 * load cachedpaperlist from database.
	 */
	private void loadCachedPaperlist()
	{
		try
		{
			/* display recently access paper. */
			AnarxivDB db = AnarxivDB.getInstance();
			List<HashMap<String, Object>>  paperMapList = AnarxivDB.paperListToMapList(db.getCachedPaperlist(-1, _paperCategory));
			//_paperMapList.clear();
			_paperMapList1.addAll(paperMapList);
				
				
					_uiPaperListAdapter1 = new SimpleAdapter(PaperListWnd.this,
							                                _paperMapList1,
															R.layout.paper_list_item,
															new String[] {"title", 
																		  "date", 
																		  "author"},
															new int[] {R.id.paperitem_title, 
																	   R.id.paperitem_date, 
																	   R.id.paperitem_author});
					_uiPaperList.setAdapter(_uiPaperListAdapter1);
				
				
				
			
			
		}
		catch (AnarxivDB.DBException e)
		{
			UiUtils.showToast(this, e.getMessage());
		}
	}
	
	
	
	/**
	 * intercept all touch event for gesture detector.
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev)
	{
		_gestureDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}
	
	/**
	 * onItemClick.
	 */
	public void onItemClick(AdapterView<?> a, View v, int position, long id) 
	{
		// TODO Auto-generated method stub
		
		// run only when there is the connection
		String service=Context.CONNECTIVITY_SERVICE;
		ConnectivityManager connectivity=(ConnectivityManager) getSystemService(service);
		NetworkInfo wifiNetwork=connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo mobiNetwork=connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if(wifiNetwork.getState()==NetworkInfo.State.CONNECTED||mobiNetwork.getState()==NetworkInfo.State.CONNECTED)
		{
			@SuppressWarnings("unchecked")
			HashMap<String, Object> item = (HashMap<String, Object>)a.getItemAtPosition(position);
			
			try
			{
				AnarxivDB db = AnarxivDB.getInstance();
				
				/* fill out the paper object and add to database. */
				AnarxivDB.Paper paper = new AnarxivDB.Paper();
				paper._author = (String)item.get("author");
				paper._date = (String)item.get("date");
				paper._id = (String)item.get("id");
				paper._title = (String)item.get("title");
				paper._url = (String)item.get("url");
				
				db.addRecentPaper(paper);
			}
			catch (AnarxivDB.DBException e)
			{
				UiUtils.showToast(this, e.getMessage());
			}
			
			
			
			if (a.getAdapter()==_uiPaperListAdapter)
			{
				Intent intent = new Intent(this, PaperDetailWnd.class);
				intent.putExtra("paperdetail", item);
				startActivity(intent);
				
			} else if (a.getAdapter()==_uiPaperListAdapter1)
			{
				Intent intent = new Intent(this, PaperDetailWnd_2.class);
				intent.putExtra("id", (String)item.get("id"));
				startActivity(intent);
			}
			
			
		}
		
		
		
	}

	/**
	 * onScroll.
	 */
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) 
	{
		
		
		// TODO Auto-generated method stub
		String service=Context.CONNECTIVITY_SERVICE;
		ConnectivityManager connectivity=(ConnectivityManager) getSystemService(service);
		NetworkInfo wifiNetwork=connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo mobiNetwork=connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if(wifiNetwork.getState()==NetworkInfo.State.CONNECTED||mobiNetwork.getState()==NetworkInfo.State.CONNECTED)
		{
			if(totalItemCount <= 0)
			{
				return;
			}
			
			if (firstVisibleItem + visibleItemCount >= _paperMapList.size() && _isLoading == false)
			{
				try
				{
					ArxivLoadingThread t = new ArxivLoadingThread();
					t.start();
						
					/* show busy box. */
					_uiBusyBox = ProgressDialog.show(this, 
													 "",
													 getResources().
													 	getText(R.string.loading_please_wait));
				}
				catch(IllegalThreadStateException e)
				{
					return;
				}
				
				_isLoading = true;
			}
		}
		
	}

	/**
	 * onScrollStateChange.
	 */
	public void onScrollStateChanged(AbsListView view, int scrollState) 
	{
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * override: context menu.
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.ctxmenu_add_to_favorite, menu);
		menu.setHeaderTitle("Add to Favorite");
	}
	
	/**
	 * override: context menu handler.
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		
		if (item.getItemId() == R.id.ctxmenu_add_to_favorite)
		{
			@SuppressWarnings("unchecked")
			HashMap<String, Object> itemData = (HashMap<String, Object>)_uiPaperList.getItemAtPosition(info.position);
			
			AnarxivDB.Paper paper = new AnarxivDB.Paper();
			paper._author = (String)itemData.get("author");
			paper._date = (String)itemData.get("date");
			paper._id = (String)itemData.get("id");
			paper._title = (String)itemData.get("title");
			paper._url = (String)itemData.get("url");
			
			try
			{
				AnarxivDB.getInstance().addFavoritePaper(paper);
				UiUtils.showToast(this, "Added to favorite: " + paper._title);
			}
			catch (AnarxivDB.DBException e)
			{
				UiUtils.showToast(this, e.getMessage());
			}
		}
		
		return super.onContextItemSelected(item);
	}
	
	/**
	 * create options menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_paperlistwnd, menu);
		return true;
	}
	

	/**
	 * handle options menu.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == R.id.menu_paperlistwind_add_to_favorite)
		{
		
			
			/* fill in category info. */
			AnarxivDB.Category category = new AnarxivDB.Category();
			category._name = _paperCategoryName;
			category._parent =_paperParentCat;
			category._queryWord = _paperCategory;
			
			/* add to db. */
			try
			{
				AnarxivDB.getInstance().addFavoriteCategory(category);
				UiUtils.showToast(this, "Added to favorite: " +_paperCategoryName);
			}
			catch (AnarxivDB.DBException e)
			{
				UiUtils.showToast(this, e.getMessage());
			}
		}
		
		
		
		return super.onOptionsItemSelected(item);
	}
	
	
}
