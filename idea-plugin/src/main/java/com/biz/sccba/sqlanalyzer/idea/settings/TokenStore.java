package com.biz.sccba.sqlanalyzer.idea.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Stores the user's API token in the IDE PasswordSafe (development-guide §8.3):
 * a secure application-level secret, never written to plain XML settings.
 */
public final class TokenStore {

    private static final String SERVICE_NAME = "SQL Performance Analyzer";
    private static final String KEY = "api-token";

    public static TokenStore getInstance() {
        return ApplicationManager.getApplication().getService(TokenStore.class);
    }

    public String token() {
        Credentials credentials = PasswordSafe.getInstance().get(attributes());
        String password = credentials == null ? null : credentials.getPasswordAsString();
        return password == null ? "" : password;
    }

    public void token(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            PasswordSafe.getInstance().set(attributes(), null);
        } else {
            PasswordSafe.getInstance().set(attributes(), new Credentials(KEY, trimmed));
        }
    }

    private static CredentialAttributes attributes() {
        return new CredentialAttributes(SERVICE_NAME, KEY);
    }
}
