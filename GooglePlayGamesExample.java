import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import static android.app.Activity.RESULT_CANCELED;
import static android.content.Context.MODE_PRIVATE;
import static com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount;
import static com.google.android.gms.games.Games.getPlayersClient;

// This file should follow this guideline ^^ https://developers.google.com/games/services/checklist
// It's made to be separated from MainActivity as is would only crouwd it

public class GooglePlayGames {

    private static final int RC_ACHIEVEMENT_UI = 9003;
    private static final int RC_LEADERBOARD_UI = 9004;

    public static int RC_SIGN_IN = 69;

    private static GoogleSignInAccount _signedInAcc = null;

// Ideally, this should be called from OnStart in MainActivity
    public static void init() {
        if (shouldAskForLogin()) {
            if (!isSignedIn()){
                GooglePlayGames.silentLogIn();
            } else {
                _signedInAcc = getLastSignedInAccount(MainActivity._Instance);
                if (_signedInAcc.isExpired()) {
                    GooglePlayGames.silentLogIn();
                }
            }
        }
        GooglePlayGames.setViewAndGravityForPopups();
    }

// You should always try the silent log in first, as stated in the guideline
    private static void silentLogIn() {
        GoogleSignInClient signInClient = GoogleSignIn.getClient(MainActivity._Instance,
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        signInClient.silentSignIn().addOnCompleteListener(MainActivity._Instance,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        if (task.isSuccessful()) {
                            _signedInAcc = task.getResult();
                        } else {
                            interactiveLogIn();
                        }
                    }
                });
    }

    private static void interactiveLogIn() {
        GoogleSignInClient signInClient = GoogleSignIn.getClient(MainActivity._Instance,
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        Intent intent = signInClient.getSignInIntent();
        MainActivity._Instance.startActivityForResult(intent, RC_SIGN_IN);
    }

// This should be called from MainActivity if the requestCode matches RC_SIGN_IN, that's why requestCode is not checked here
    public static void onActivityResult(Intent data, int resultCode){
        if (resultCode == RESULT_CANCELED) {
            // If the player has chosen not to log in, we should remember it and not ask him again ^^
            SharedPreferences.Editor editor = MainActivity._Instance.getSharedPreferences("google_play_games", MODE_PRIVATE).edit();
            editor.putString("log_in_preference","false");
            editor.apply();
            return;
        }

        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
        if (result.isSuccess()) {
            _signedInAcc = result.getSignInAccount();

            PlayersClient signedInClient = getPlayersClient(MainActivity._Instance, _signedInAcc);
            signedInClient.getCurrentPlayer()
                    .addOnCompleteListener(new OnCompleteListener<Player>() {
                        @Override
                        public void onComplete(Task<Player> pTask) {
                            if(pTask.isSuccessful()){
                                Player player = pTask.getResult();
                                handlePlayer(player);
                            } else {
                                new AlertDialog.Builder(MainActivity._Instance).setMessage("Unable to log in to google services :( ")
                                        .setNeutralButton(android.R.string.ok, null).show();
                            }

                        }
                    });
        } else {
            new AlertDialog.Builder(MainActivity._Instance).setMessage("Unable to log in to google services :( ")
                    .setNeutralButton(android.R.string.ok, null).show();
        }
    }

    private static void setViewAndGravityForPopups() {
        if(_signedInAcc == null) {
            return;
        }
        Games.getGamesClient(getContext(), _signedInAcc).setViewForPopups(MainActivity._Instance.findViewById(android.R.id.content));
        Games.getGamesClient(MainActivity._Instance, _signedInAcc).setGravityForPopups(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    }

    private static boolean isSignedIn() {
        return getLastSignedInAccount(MainActivity._Instance) != null;
    }

// Check log in preference (should you ask the player if he already refused to connect)
    private static boolean shouldAskForLogin() {
        SharedPreferences prefs = MainActivity._Instance.getSharedPreferences("google_play_games", MODE_PRIVATE);
        String wantsToLogIn = prefs.getString("log_in_preference", null);

        if (wantsToLogIn == null) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("log_in_preference","true");
            editor.apply();
            return true;

        } else if (wantsToLogIn.equalsIgnoreCase("true")) {
            return true;
        }
        return false;
    }

// Everything that you need to do when the player is retrieved.
// In this case it's just setting the logging preference
    private static void handlePlayer(Player player) {
        SharedPreferences.Editor editor = MainActivity._Instance.getSharedPreferences("google_play_games", MODE_PRIVATE).edit();
        editor.putString("log_in_preference","true");
        editor.apply();
    }

    private static void unlockAchievement(String name) {
        if(_signedInAcc == null) {
            return;
        }
        String achievement = MainActivity._Instance.getStringResourceByName(name);
        if(achievement.isEmpty()) {
            return;
        }
        Games.getAchievementsClient(MainActivity._Instance, getLastSignedInAccount(MainActivity._Instance))
                .unlock(achievement);
    }

    private static void incrementAchievement(int amount, String name) {
        if(_signedInAcc == null) {
            return;
        }
        String achievement = MainActivity._Instance.getStringResourceByName(name);
        if(achievement.isEmpty()) {
            return;
        }
        Games.getAchievementsClient(MainActivity._Instance, getLastSignedInAccount(MainActivity._Instance))
                .increment(achievement,amount);
    }

    public static void showAchievements() {
        if(_signedInAcc == null) {
            return;
        }
        Games.getAchievementsClient(MainActivity._Instance, getLastSignedInAccount(MainActivity._Instance))
                .getAchievementsIntent()
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        MainActivity._Instance.startActivityForResult(intent, RC_ACHIEVEMENT_UI);
                    }
                });
    }

    private static void showLeaderboard(String leaderboard) {
        if(_signedInAcc == null) {
            return;
        }
        Games.getLeaderboardsClient(MainActivity._Instance, getLastSignedInAccount(MainActivity._Instance))
                .getLeaderboardIntent(MainActivity._Instance.getStringResourceByName(leaderboard))
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        MainActivity._Instance.startActivityForResult(intent, RC_LEADERBOARD_UI);
                    }
                });
    }

    private static void updateLeaderboardScore(int score, String leaderboard) {
        if(_signedInAcc == null) {
            return;
        }
        Games.getLeaderboardsClient(MainActivity._Instance, getLastSignedInAccount(MainActivity._Instance))
                .submitScore(MainActivity._Instance.getStringResourceByName(leaderboard), score);
    }

    private static void signOut() {
        _signedInAcc = null;
        GoogleSignInClient signInClient = GoogleSignIn.getClient(MainActivity._Instance,
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        signInClient.signOut().addOnCompleteListener(MainActivity._Instance,
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        // at this point, the user is signed out ^^
                    }
                });
    }
}
