package edu.hitwh.fieldnote;
import android.app.Activity;
import android.os.Build;
import android.view.*;
import android.widget.FrameLayout;
final class UiInsets {
 static void install(Activity a,View v){
  FrameLayout root=new FrameLayout(a);root.setBackgroundColor(0xfff1f3ec);root.addView(v,new FrameLayout.LayoutParams(-1,-1));a.setContentView(root);
  if(Build.VERSION.SDK_INT>=30){a.getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
  else root.setFitsSystemWindows(true);
 }
}
