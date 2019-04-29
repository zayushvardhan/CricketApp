package com.example.cricketapp;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.DialogInterface;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cricketapp.adapters.MainAdapter;
import com.example.cricketapp.models.Message;
import com.example.cricketapp.utils.ProfanityFilter;
import com.example.cricketapp.utils.SCUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final int ANTI_FLOOD_SECONDS = 3; //simple anti-flood
    private boolean IS_ADMIN = false; //set this to true for the admin app.
    private String username = "anonymous"; //default username
    private boolean PROFANITY_FILTER_ACTIVE = true;
    private FirebaseDatabase database;
    private RecyclerView main_recycler_view;
    private String userID;
    private MainActivity mContext;
    private MainAdapter adapter;
    private DatabaseReference databaseRef;
    private ImageButton imageButton_send;
    private EditText editText_message;
    private ListView dashboard;
    ArrayList<Message> messageArrayList = new ArrayList<>();
    private ProgressBar progressBar;
    private long last_message_timestamp = 0;
    private Message new_message;
    private ArrayList<String> liveMatch;
    private ArrayAdapter match_adapter;
    private TextView loading;

    private FirebaseAuth mAuth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mContext = MainActivity.this;
        main_recycler_view = (RecyclerView) findViewById(R.id.main_recycler_view);
        imageButton_send = (ImageButton) findViewById(R.id.imageButton_send);
        editText_message = (EditText) findViewById(R.id.editText_message);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        database = FirebaseDatabase.getInstance();
        dashboard = (ListView) findViewById(R.id.common_dashboard);
        databaseRef = database.getReference();
        loading =(TextView)findViewById(R.id.matches_loading);

        databaseRef.child("test_message").setValue("Hello");

        progressBar.setVisibility(View.VISIBLE);
        main_recycler_view.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MainAdapter(mContext, messageArrayList);
        main_recycler_view.setAdapter(adapter);

        liveMatch =new ArrayList<String>();

        new Thread(new Runnable() {
            @Override
            public void run() {

                Document doc;

                try{

                    doc = Jsoup.connect("https://www.cricbuzz.com/cricket-match/live-scores").get();
                    Elements content = doc.getElementsByClass("cb-col cb-col-100 cb-lv-main");

                    for (Element link : content) {
                        Elements links = link.getElementsByTag("h2");
                        for (Element head : links) {
                            String linkText = head.text();
                            liveMatch.add("> "+linkText);
                        }
                    }

                    Log.d("Title of CricBuzz",doc.title());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                            match_adapter = new ArrayAdapter<String>(MainActivity.this,R.layout.activity_listview, liveMatch);
                            dashboard.setAdapter(match_adapter);
                            //dashboard.setText(builder.toString());
                        }
                    });

                }catch (IOException e){

                }
            }
        }).start();

        dashboard.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d("Item Clicked: ",match_adapter.getItem(position).toString());
            }
        });

        databaseRef.child("the_messages").addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                //Toast.makeText(MainActivity.this,"onChildAdded",Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);

                new_message = dataSnapshot.getValue(Message.class);

                if(adapter.getItemCount()>0){
                    String msg = messageArrayList.get(adapter.getItemCount()-1).getMessage();
                    Log.d("MESSAGE RECEIVED : ", msg);

                    if (!msg.equals(new_message.getMessage())){
                        messageArrayList.add(new_message);
                        adapter.notifyDataSetChanged();
                        main_recycler_view.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }else{
                    messageArrayList.add(new_message);
                    adapter.notifyDataSetChanged();
                    main_recycler_view.scrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                //Toast.makeText(MainActivity.this,"onChildChanged",Toast.LENGTH_SHORT).show();
            }

            @Override public void onChildRemoved(DataSnapshot dataSnapshot) {
                //Toast.makeText(MainActivity.this,"onChildRemoved",Toast.LENGTH_SHORT).show();
                Log.d("REMOVED", dataSnapshot.getValue(Message.class).toString());
                messageArrayList.remove(dataSnapshot.getValue(Message.class));
                adapter.notifyDataSetChanged();
            }

            @Override public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                //Toast.makeText(MainActivity.this,"onChildMoved",Toast.LENGTH_SHORT).show();
            }

            @Override public void onCancelled(DatabaseError databaseError) {
                //Toast.makeText(MainActivity.this,"onCancelled",Toast.LENGTH_SHORT).show();
            }
        });

        imageButton_send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {

                final String message = editText_message.getText().toString().trim();
                Message xmessage = new Message(userID, username, message, System.currentTimeMillis() / 1000L, IS_ADMIN, false);

                messageArrayList.add(xmessage);
                adapter.notifyDataSetChanged();
                editText_message.setText("");
                //flag_old_message = 1;

                main_recycler_view.scrollToPosition(adapter.getItemCount() - 1);

                Thread thread = new Thread(){
                    public void run(){
                        Log.d("Thread Notification : ", "Hi! This is created by Ayush");
                        process_new_message(message, false);
                    }
                };

                thread.start();
            }
        });

        editText_message.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_SEND)) {
                    imageButton_send.performClick();
                }
                return false;
            }
        });

        logic_for_username();

    }

    private void process_new_message(String new_message, boolean isNotification) {
        if (new_message.isEmpty()) {
            return;
        }

        //simple anti-flood protection
        if ((System.currentTimeMillis() / 1000L - last_message_timestamp) < ANTI_FLOOD_SECONDS) {
            SCUtils.showErrorSnackBar(mContext, findViewById(android.R.id.content), "You cannot send messages so fast.").show();
            return;
        }

        //yes, admins can swear ;)
        if ((PROFANITY_FILTER_ACTIVE) && (!IS_ADMIN)) {
            new_message = new_message.replaceAll(ProfanityFilter.censorWords(ProfanityFilter.ENGLISH), ":)");
        }

        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        }); */

        Message xmessage = new Message(userID, username, new_message, System.currentTimeMillis() / 1000L, IS_ADMIN, isNotification);

        String key = databaseRef.child("the_messages").push().getKey();
        databaseRef.child("the_messages").child(key).setValue(xmessage);

        last_message_timestamp = System.currentTimeMillis() / 1000L;
    }

    //Popup message with your username if none found. Change it to your liking
    private void logic_for_username() {
        userID = SCUtils.getUniqueID(getApplicationContext());
        databaseRef.child("users").child(userID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot dataSnapshot) {
                progressBar.setVisibility(View.GONE);
                if (!dataSnapshot.exists()) {
                    show_alert_username();
                } else {
                    username = dataSnapshot.getValue(String.class);
                    Snackbar.make(findViewById(android.R.id.content), "Logged in as " + username, Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override public void onCancelled(DatabaseError databaseError) {
                Log.w("!!!", "username:onCancelled", databaseError.toException());
            }
        });
    }

    private void show_alert_username() {
        AlertDialog.Builder alertDialogUsername = new AlertDialog.Builder(mContext);
        alertDialogUsername.setMessage("Your username");
        final EditText input = new EditText(mContext);
        input.setText(username);
        alertDialogUsername.setView(input);

        alertDialogUsername.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {

            @Override public void onClick(DialogInterface dialog, int id) {
                String new_username = input.getText().toString().trim();
                if ((!new_username.equals(username)) && (!username.equals("anonymous"))) {
                    process_new_message(username + " changed it's nickname to " + new_username, true);
                }
                username = new_username;
                databaseRef.child("users").child(userID).setValue(username);
            }
        }).setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {

            @Override public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        alertDialogUsername.show();
    }

    public void userLogin(View view) {
        startActivity(new Intent(MainActivity.this,dashboad.class));
    }
}
