package com.company.cyranomini.uihelpers;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.company.cyranomini.R;

public class IconWithTextView extends LinearLayout {
    private ImageView imageView;
    private TextView textView;

    public IconWithTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.icon_with_text, this, true);

        imageView = view.findViewById(R.id.ivIcon);
        textView = view.findViewById(R.id.tvTitle);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.IconWithTextView);
        int iconResId = typedArray.getResourceId(R.styleable.IconWithTextView_iconSrc, R.drawable.status_add);
        String text = typedArray.getString(R.styleable.IconWithTextView_text);

        imageView.setImageResource(iconResId);
        textView.setText(text);

        typedArray.recycle();
    }

    // Custom setters to change properties programmatically
    public void setIcon(int resId) {
        imageView.setImageResource(resId);
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setFilter(int colorPrimary) {
        Drawable originalDrawable = imageView.getDrawable();
        Drawable wrappedDrawable = DrawableCompat.wrap(originalDrawable);
        DrawableCompat.setTint(wrappedDrawable, ContextCompat.getColor(getContext(),colorPrimary)); // Set the color you want
        imageView.setImageDrawable(wrappedDrawable);
    }
}
