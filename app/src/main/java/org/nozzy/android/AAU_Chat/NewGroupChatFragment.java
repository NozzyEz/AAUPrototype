package org.nozzy.android.AAU_Chat;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import id.zelory.compressor.Compressor;

import static android.app.Activity.RESULT_OK;

public class NewGroupChatFragment extends BaseFragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    private RecyclerView mUsersList;
    private DatabaseReference mUsersDatabase;
    private DatabaseReference mFriendsDatabase;
    private FirebaseAuth mAuth;
    private String mCurrentUserID;
    private EditText etSearch;
    private DatabaseReference mRootRef;
    private String push_id;
    private ImageButton icon_group;
    private AlertDialog.Builder dialogBuilder;

    public static String searchString = "";
    public static String name;
    private static final int GALLERY_PICK = 1;
    private boolean addedImage = false;

    private StorageReference mImageStorage;

    private ProgressDialog mProgressDialog;

    private ArrayList<String> users = new ArrayList<>();

    public static String getFragmentTitle() {
        return "New Group Chat";
    }

    @Override
    protected int getCurrentFragmentLayout() {
        return R.layout.fragment_new_group_chat;
    }

    public static String getFragmentTag() {
        return TAG;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Here we setup our RecyclerView which we use to show all the users, one by one
        mUsersList = getView().findViewById(R.id.users_list);
        dialogBuilder = new AlertDialog.Builder(getContext());

        mImageStorage = FirebaseStorage.getInstance().getReference();

        final FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!users.isEmpty()) {

                    mRootRef = FirebaseDatabase.getInstance().getReference();
                    DatabaseReference chat_push = mRootRef.child("Chats").push();
                    push_id = chat_push.getKey();
                    showReportInput();

                } else {
                    Snackbar.make(view, "Select Users to start a new group chat", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });

        mUsersList.setHasFixedSize(true);
        mUsersList.setLayoutManager(new LinearLayoutManager(getContext()));

        // and finally we point our database reference to the Users database
        mUsersDatabase = FirebaseDatabase.getInstance().getReference().child("Users");
        mUsersDatabase.keepSynced(true);
        mFriendsDatabase = FirebaseDatabase.getInstance().getReference().child("Friends");
        mFriendsDatabase.keepSynced(true);

        mAuth = FirebaseAuth.getInstance();
        mCurrentUserID = mAuth.getCurrentUser().getUid();

        etSearch = getView().findViewById(R.id.etSearch);
        setupSearchInput();
    }

    @Override
    public void onStart() {
        super.onStart();

        mUsersDatabase.child(mCurrentUserID).child("online").setValue("true");

        mUsersDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                // Gets all the relevant user's data from the database
                name = dataSnapshot.child(mCurrentUserID).child("name").getValue().toString();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        searchUser(searchString);
    }

    private void setupSearchInput() {
        //Allows to search by pressing enter button on keyboard
        etSearch.clearFocus();
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    searchString = etSearch.getText().toString();
                    searchUser(searchString);
                    // Hides the keyboard
                    if (getActivity().getWindow() != null && getActivity().getWindow().getDecorView() != null && getActivity().getWindow().getDecorView().getWindowToken() != null) {
                        InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        mgr.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            }
        });
    }
    private void showReportInput() {
        dialogBuilder = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_new_chat, null);

        icon_group = dialogView.findViewById(R.id.icon_group);
        icon_group.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Creates an intent for the default gallery picker
                Intent galleryIntent = new Intent();
                galleryIntent.setType("image/*");
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                // Starts the activity with the request code GALLERY_PICK, which is caught in the onActivityResult method
                startActivityForResult(Intent.createChooser(galleryIntent, "SELECT IMAGE"), GALLERY_PICK);
            }
        });

        dialogBuilder.setView(dialogView);
        final EditText edt = dialogView.findViewById(R.id.etChatName);
        dialogBuilder.setTitle("Add a group name and image");
        //dialogBuilder.setMessage("Enter text below");
        dialogBuilder.setPositiveButton(getResources().getString(R.string.new_chat), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String message = edt.getText().toString().trim();
                sendToChat(users,message);

            }
        });
        dialogBuilder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });
        AlertDialog b = dialogBuilder.create();
        b.show();
    }

    private void sendToChat(ArrayList<String> list_users, String chatName) {

        if (chatName.equals("")) chatName = "New Chat";

        // Generates chat ID
        // Adding the chat with timestamp to the Users table
        mRootRef.child("Users").child(mCurrentUserID).child("chats").child(push_id).child("timestamp").setValue(ServerValue.TIMESTAMP);
        for (String user : list_users) {
            mRootRef.child("Users").child(user).child("chats").child(push_id).child("timestamp").setValue(ServerValue.TIMESTAMP);
        }
        // Creating the chat in the Chats table with members, name, type, image and seen values
        mRootRef.child("Chats").child(push_id).child("members").child(mCurrentUserID).setValue("admin");
        for (String user : list_users) {
            mRootRef.child("Chats").child(push_id).child("members").child(user).setValue("user");
            mRootRef.child("Chats").child(push_id).child("seen").child(user).setValue("");
        }
        mRootRef.child("Chats").child(push_id).child("chat_name").setValue(chatName);
        mRootRef.child("Chats").child(push_id).child("chat_type").setValue("group");
        mRootRef.child("Chats").child(push_id).child("seen").child(mCurrentUserID).setValue("");
        if(!addedImage) {
            mRootRef.child("Chats").child(push_id).child("chat_image").setValue("");
        }

        // Passing variables and starting ChatActivity
        Intent chatIntent = new Intent(getContext(), ChatActivity.class);
        chatIntent.putExtra("chat_id", push_id);
        startActivity(chatIntent);
    }

    private void searchUser(String searchString) {
        // Query is being passed with information from EditText
        Query searchQuery = mFriendsDatabase.child(mCurrentUserID).orderByChild("name").startAt(searchString).endAt(searchString + "\uf8ff");

        // Adapts the users friends to the RecyclerView
        FirebaseRecyclerAdapter<Friends, UsersFragment.FriendsViewHolder> friendsRecyclerViewAdapter = new FirebaseRecyclerAdapter<Friends, UsersFragment.FriendsViewHolder>(
                Friends.class,
                R.layout.users_single_layout_new_chat,
                UsersFragment.FriendsViewHolder.class,
                searchQuery
        ) {
            @Override
            // This method is used to populate our RecyclerView with each of our friends
            protected void populateViewHolder(final UsersFragment.FriendsViewHolder friendsViewHolder, final Friends friends, int position) {

                // Gets the ID of the friend
                final String list_user_id = getRef(position).getKey();

                // Adds a listener to each friend in the database in order to update the list in real time
                mUsersDatabase.child(list_user_id).addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        final String userName = dataSnapshot.child("name").getValue().toString();
                        final String userStatus = dataSnapshot.child("status").getValue().toString();
                        final String userThumb = dataSnapshot.child("thumb_image").getValue().toString();

                        // Inside each view we retrieve the value for name, date and image with our Friends class, and set it to the view as needed
                        friendsViewHolder.setName(userName);
                        friendsViewHolder.setStatus(userStatus);
                        friendsViewHolder.setThumb(userThumb, getContext());

                        // When the user clicks on the view, we then pass the correct user ID to the intent
                        // that leads them to the ProfileActivity, so that we can show the correct information
                        // in that Activity
                        if(users.contains(list_user_id)){
                            friendsViewHolder.checkBox.setChecked(true);
                        } else {
                            friendsViewHolder.checkBox.setChecked(false);
                        }
                        friendsViewHolder.mView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                    if(users.contains(list_user_id)){
                                        users.remove(users.indexOf(list_user_id));
                                    } else {
                                        users.add(list_user_id);
                                    }
                                    Log.e ("List",(Arrays.toString(users.toArray())));
                                    friendsViewHolder.changeState();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
            }
        };

        // Finally, puts all of the friends into our RecyclerView
        mUsersList.setAdapter(friendsRecyclerViewAdapter);

    }

    @Override
    public void onPause() {
        super.onPause();
        mUsersDatabase.child(mCurrentUserID).child("online").setValue(ServerValue.TIMESTAMP);

    }

    @Override
    public void onResume(){
        super.onResume();
        searchUser(searchString);
    }

    @Override
    // We use this method when an image is being picked, in here the user picks an image from their
    // external storage, we crop it, and we store it with firebase. We also compress it so that we
    // can use it for a thumbnail image, which we in turn also store
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Checks if the activity was the default gallery picker
        if(requestCode == GALLERY_PICK && resultCode == RESULT_OK) {

            // Gets the picked image
            Uri imageUri = data.getData();

            // Starts the image cropper activity
            CropImage.activity(imageUri)
                    .setAspectRatio(1,1)
                    .setMinCropWindowSize(500, 500)
                    .start(getActivity(), this);
        }

        // Checks if the activity was the image cropper
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {

            // Gets the result from the image cropper activity
            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if (resultCode == RESULT_OK) {

                // Shows a progress dialog (loading screen) until the images are uploaded
                mProgressDialog = new ProgressDialog(getContext());
                mProgressDialog.setTitle("Uploading Image");
                mProgressDialog.setMessage("Please wait while the image is being uploaded");
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressDialog.show();

                // Gets the cropped image
                Uri resultUri = result.getUri();
                // Creates a thumbnail image file (with the path of the cropped one) for compression
                final File thumb_filePath = new File(resultUri.getPath());

                // Gets the user's ID - this will be the name of the profile picture when storing
                //String current_user_id = mCurrentUser.getUid();
                // A byte array for storing the image
                byte[] thumb_byte = new byte[0];
                try {
                    // Creates a compressor, loads the image onto it
                    Bitmap thumb_bitmap = new Compressor(getActivity())
                            .setMaxWidth(200)
                            .setMaxHeight(200)
                            .setQuality(75)
                            .compressToBitmap(thumb_filePath);
                    // A BAOS stores the resulting image data after compression,
                    // which we then transfer to a byte array
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    thumb_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    thumb_byte = baos.toByteArray();
                } catch (IOException e) { e.printStackTrace(); }

                // Creates references with filepaths for the image and the thumbnail to be stored in the database
                //StorageReference filepath = mImageStorage.child("profile_images").child(push_id + ".jpg");
                final StorageReference thumb_filepath = mImageStorage.child("chat_images").child(push_id + ".jpg");

                // Another byte array for the thumb image (God knows why)
                final byte[] finalThumb_byte = thumb_byte;

                // Creates a task to upload the compressed image in the form of a byte array to the database
                UploadTask uploadTask = thumb_filepath.putBytes(finalThumb_byte);
                uploadTask.addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> thumb_task) {
                        if(thumb_task.isSuccessful()) {
                            // Gets the download link to the thumbnail that we just uploaded
                            String thumb_downloadUrl = thumb_task.getResult().getDownloadUrl().toString();

                            mRootRef.child("Chats").child(push_id).child("chat_image").setValue(thumb_downloadUrl);
                            mProgressDialog.dismiss();
                            addedImage = true;

                        } else {
                            // Shows a toast if the thumbnail upload was unsuccessful
                            Toast.makeText(getContext(), "Error in uploading thumbnail", Toast.LENGTH_LONG).show();
                            mProgressDialog.dismiss();

                        }

                    }
                });

            }
        }
    }
}
