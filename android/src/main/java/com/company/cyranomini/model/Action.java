package com.company.cyranomini.model;

import android.content.Context;
import android.view.ViewGroup;

import com.company.cyranomini.R;
import com.company.cyranomini.uihelpers.IconWithTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class Action {
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getIconRes() {
        return iconRes;
    }

    public void setIconRes(int iconRes) {
        this.iconRes = iconRes;
    }

    public List<Action> getOptions() {
        return options;
    }

    public void setOptions(ArrayList<Action> options) {
        this.options = options;
    }

    public IconWithTextView getView(Context c)
    {
        if(view == null)
        {
            view = new IconWithTextView(c,null);
        }
        view.setText(title);
        view.setIcon(iconRes);
        return view;
    }


    private String title;
    private int iconRes;

    public boolean isDisplayedOptions() {
        return displayedOptions;
    }

    public void setDisplayedOptions(boolean displayedOptions) {
        this.displayedOptions = displayedOptions;
        if(displayedOptions)
        {
            view.setFilter(R.color.colorPrimary);
        }else
        {
            view.setFilter(R.color.colorAccent);
        }
    }

    private boolean displayedOptions;
    private ViewGroup container;
    private List<Action> options;

    private Callable<Void> callable;

    public Action(String title, int iconRes, List<Action> options, Callable<Void> callable, ActionType type) {
        this.title = title;
        this.iconRes = iconRes;
        this.options = options;
        this.callable = callable;
        this.type = type;
        this.view = view;
    }

    private ActionType type = ActionType.OPTION;

    private IconWithTextView view ;

    public ViewGroup getContainer() {
        return container;
    }

    public void setContainer(ViewGroup container) {
        this.container = container;
        setDisplayedOptions(container != null);

    }

    public Callable<Void> getCallable() {
        return callable;
    }

    public void setCallable(Callable<Void> callable) {
        this.callable = callable;
    }

    public enum ActionType{
        OPTION,
        CHATBOX

    }
}