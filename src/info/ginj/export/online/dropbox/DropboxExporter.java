package info.ginj.export.online.dropbox;

import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.sharing.SharedLinkMetadata;
import com.dropbox.core.v2.sharing.SharedLinkSettings;
import com.dropbox.core.v2.users.FullAccount;
import info.ginj.Capture;
import info.ginj.Ginj;
import info.ginj.Prefs;
import info.ginj.export.online.AbstractOnlineExporter;
import info.ginj.export.online.exception.AuthorizationException;
import info.ginj.export.online.exception.CommunicationException;
import info.ginj.export.online.exception.UploadException;
import info.ginj.ui.Util;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;


public class DropboxExporter extends AbstractOnlineExporter {
    private static final String APP_KEY = "pdio3i9brehyjo1";


    public DropboxExporter(JFrame frame) {
        super(frame);
    }


    @Override
    public String getServiceName() {
        return "Dropbox";
    }


    /**
     * This method checks that Dropbox authentication is valid, and if not, an authentication procedure is performed.
     *
     * TODO re-implement using home-made http layer
     *
     * @param accountNumber the number of this account (in case multiple Dropbox accounts are configured
     * @return true if authentication was successful.
     */
    @Override
    public boolean prepare(Capture capture, String accountNumber) {
        logProgress("Checking authorization", 5);
        String userToken = Prefs.getWithSuffix(Prefs.Key.EXPORTER_DROPBOX_ACCESS_TOKEN_PREFIX, accountNumber);
        if (userToken != null) {
            String userName = null;
            try {
                userName = checkAuthorization(userToken);
            }
            catch (Exception e) {
                // noop
            }
            if (userName != null) {
                return true;
            }
        }

        // If we get here, authentication failed, or has never been made.
        // Start authentication procedure

        DbxRequestConfig requestConfig = new DbxRequestConfig("Ginj");
        DbxAppInfo appInfoWithoutSecret = new DbxAppInfo(APP_KEY);
        DbxPKCEWebAuth pkceWebAuth = new DbxPKCEWebAuth(requestConfig, appInfoWithoutSecret);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .withTokenAccessType(TokenAccessType.OFFLINE)
                .build();

        String url = pkceWebAuth.authorize(webAuthRequest);

        String code = JOptionPane.showInputDialog(getFrame(),
                Util.createClickableHtmlEditorPane(
                        "Please visit <a href='" + url + "'>this Dropbox page</a>, <br>allow " + Ginj.getAppName() + " (you may have to log in to Dropbox first), <br>copy the generated authorization key and paste it below."
                ),
                "Dropbox authentication", JOptionPane.QUESTION_MESSAGE
        );

        if (code != null) {
            code = code.trim();
            try {
                DbxAuthFinish authFinish = pkceWebAuth.finishFromCode(code);
                userToken = authFinish.getAccessToken();
                String userName = checkAuthorization(userToken);
                if (userName != null) {
                    Prefs.setWithSuffix(Prefs.Key.EXPORTER_DROPBOX_ACCESS_TOKEN_PREFIX, accountNumber, userToken);
                    Prefs.setWithSuffix(Prefs.Key.EXPORTER_DROPBOX_USERNAME_PREFIX, accountNumber, userName);
                    // Dropbox does not use the following fields for now. Tokens currently never expire.
                    // Prefs.setWithSuffix(Prefs.Key.EXPORTER_DROPBOX_REFRESH_TOKEN_PREFIX, accountNumber, authFinish.getRefreshToken());
                    // Prefs.setWithSuffix(Prefs.Key.EXPORTER_DROPBOX_EXPIRES_AT_PREFIX, accountNumber, String.valueOf(authFinish.getExpiresAt()));
                    JOptionPane.showMessageDialog(getFrame(), "You are successfully authenticated to Dropbox as " + userName, "Dropbox authentication", JOptionPane.INFORMATION_MESSAGE);
                    return true;
                }
            }
            catch (DbxException e) {
                Util.alertException(getFrame(), "Dropbox authentication", "Dropbox authentication error", e);
            }
        }
        return false;
    }

    /**
     * Exports the given capture
     * This method is run in its own thread and should not access the GUI directly. All interaction
     * should go through synchronized objects or be enclosed in a SwingUtilities.invokeLater() logic
     *
     * TODO re-implement using home-made http layer
     *
     * @param capture        the capture to export
     * @param accountNumber  the accountNumber to export this capture to
     */
    @Override
    public void exportCapture(Capture capture, String accountNumber) {
        logProgress("Preparing pload", 10);
        final String targetFileName = "/Applications/" + Ginj.getAppName() + "/" + capture.getDefaultName() + ".png";
        try {
            String accessToken = Prefs.getWithSuffix(Prefs.Key.EXPORTER_DROPBOX_ACCESS_TOKEN_PREFIX, accountNumber);
            DbxRequestConfig config = new DbxRequestConfig(Ginj.getAppName() + "/" + Ginj.getVersion());
            DbxClientV2 client = new DbxClientV2(config, accessToken); // TODO move to exportSettings to support multi-account

            // TODO Upload should be done using sessions - uploadSessionStart etc.
            // Required for big files, but also for progress
            final File fileToUpload = capture.toFile();
            if (fileToUpload.length() < 150_000_000) {
                try (InputStream in = new FileInputStream(fileToUpload)) {
                    logProgress("Uploading file", 50);
                    client.files().uploadBuilder(targetFileName).uploadAndFinish(in);
                    if (Prefs.isTrueWithSuffix(Prefs.Key.EXPORTER_DROPBOX_CREATE_LINK_PREFIX, accountNumber)) {
                        final SharedLinkMetadata sharedLinkMetadata = client.sharing().createSharedLinkWithSettings(targetFileName, new SharedLinkSettings());
                        copyTextToClipboard(sharedLinkMetadata.getUrl()); // TODO make this optional ?
                        complete("Upload successful. A link to your capture was copied to the clipboard");
                    }
                }
            }
            else {
                failed("Upload of big files not implemented yet");
            }
        }
        catch (Exception e) {
            Util.alertException(getFrame(), "Upload error", "Error uploading to Dropbox: ", e);
            failed("Upload error");
        }
    }

    /**
     * Should work, but more work is needed to finish the transaction after the code has been entered.
     * Note: for PKCE, see https://auth0.com/docs/flows/guides/auth-code-pkce/add-login-auth-code-pkce
     *
     * @return the url to call
     */
    private String getAuthenticationUrlByHand() {
        try {
            // Create a Code Verifier
            SecureRandom sr = new SecureRandom();
            byte[] code = new byte[32];
            sr.nextBytes(code);
            final Base64.Encoder encoder = Base64.getUrlEncoder();
            String verifier = encoder.encodeToString(code);

            // Create a Code Challenge
            byte[] bytes = verifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes, 0, bytes.length);
            byte[] digest = md.digest();
            String challenge = encoder.encodeToString(digest).replaceAll("=+$", ""); // remove trailing equal;

            // Authorize the User
            return "https://www.dropbox.com/oauth2/authorize?client_id=" + APP_KEY
                    + "&response_type=code&code_challenge=" + challenge
                    + "&code_challenge_method=S256";
        }
        catch (NoSuchAlgorithmException e) {
            System.err.println("Error authenticating user to Dropbox: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * This method checks that Dropbox authentication is valid
     *
     * @param accessToken The Dropbox access token to test
     * @return the name of the account's owner
     * @throws DbxException in case authentication failed
     */
    private String checkAuthorization(String accessToken) throws DbxException {
        // Create Dropbox client
        DbxRequestConfig config = new DbxRequestConfig(Ginj.getAppName() + "/" + Ginj.getVersion());
        DbxClientV2 client = new DbxClientV2(config, accessToken);

        // Get current account info
        FullAccount account = client.users().getCurrentAccount();
        return account.getName().getDisplayName();
    }


    @Override
    public void authorize(String accountNumber) throws AuthorizationException {

    }

    @Override
    public void abortAuthorization() {

    }

    @Override
    public void checkAuthorizations(String accountNumber) throws CommunicationException, AuthorizationException {

    }

    @Override
    public String uploadCapture(Capture capture, String accountNumber) throws AuthorizationException, UploadException, CommunicationException {
        return null;
    }


}