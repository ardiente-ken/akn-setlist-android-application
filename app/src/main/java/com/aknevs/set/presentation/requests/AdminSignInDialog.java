package com.aknevs.set.presentation.requests;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.aknevs.set.R;
import com.aknevs.set.domain.model.AdminException;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.usecase.ManageRequestsUseCase;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Email + password sign-in for the performer. Stays open on failure and explains why. */
public final class AdminSignInDialog {

    private AdminSignInDialog() {}

    public static void show(Context context, ManageRequestsUseCase requests) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_admin_sign_in, null);
        EditText email = view.findViewById(R.id.edit_admin_email);
        EditText password = view.findViewById(R.id.edit_admin_password);
        TextView error = view.findViewById(R.id.text_admin_error);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.admin_title)
                .setView(view)
                .setPositiveButton(R.string.admin_sign_in, null) // set below so it doesn't auto-dismiss
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();

        Button signIn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        signIn.setOnClickListener(v -> {
            String emailText = email.getText().toString().trim();
            String passwordText = password.getText().toString();
            if (emailText.isEmpty() || passwordText.isEmpty()) {
                showError(error, R.string.admin_error_missing);
                return;
            }
            error.setVisibility(View.GONE);
            signIn.setEnabled(false);
            signIn.setText(R.string.admin_signing_in);

            requests.signInAdmin(emailText, passwordText, new RepositoryCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean isAdmin) {
                    if (!dialog.isShowing()) return;
                    if (isAdmin) {
                        dialog.dismiss(); // the screen updates itself from the admin state
                    } else {
                        reset(signIn);
                        showError(error, R.string.admin_error_not_admin);
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (!dialog.isShowing()) return;
                    reset(signIn);
                    showError(error, messageFor(e));
                }
            });
        });
    }

    private static void reset(Button button) {
        button.setEnabled(true);
        button.setText(R.string.admin_sign_in);
    }

    private static void showError(TextView view, @StringRes int message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    @StringRes
    private static int messageFor(Exception e) {
        if (e instanceof AdminException) {
            switch (((AdminException) e).getReason()) {
                case INVALID_CREDENTIALS: return R.string.admin_error_credentials;
                case NETWORK:             return R.string.admin_error_network;
                default:                  break;
            }
        }
        return R.string.admin_error_generic;
    }
}
