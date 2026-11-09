package dev.watchtrigger.probe;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setPadding(48, 96, 48, 48);
        status.setTextSize(18);
        setContentView(status);

        Probe.registerAccount(this);

        RoleManager roles = getSystemService(RoleManager.class);
        boolean available = roles.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION);
        boolean held = roles.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION);
        Log.i(Probe.TAG, "role available=" + available + " held=" + held);
        if (available && !held) {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION), 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RoleManager roles = getSystemService(RoleManager.class);
        status.setText("Watch Trigger Probe\n\n"
                + "Call redirection available: " + roles.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION) + "\n"
                + "Call redirection held: " + roles.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) + "\n\n"
                + Probe.CODE_TRIGGER + " = trigger test\n"
                + Probe.CODE_VOICE + " = voice test\n\n"
                + "All other numbers pass through unchanged.");
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        Log.i(Probe.TAG, "role request result=" + result);
    }
}
