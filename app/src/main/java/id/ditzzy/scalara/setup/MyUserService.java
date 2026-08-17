package id.ditzzy.scalara.setup;

import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MyUserService extends IUserService.Stub {

    @Override
    public String grantSecureSettings(String packageName) throws RemoteException {
        try {
            java.lang.Process process = Runtime.getRuntime().exec(
                    new String[]{
                            "pm",
                            "grant",
                            packageName,
                            "android.permission.WRITE_SECURE_SETTINGS"
                    }
            );

            int exitCode = process.waitFor();

            BufferedReader stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            BufferedReader stderrReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
            );

            StringBuilder output = new StringBuilder();

            String line;

            while ((line = stdoutReader.readLine()) != null) {
                output.append(line).append('\n');
            }

            while ((line = stderrReader.readLine()) != null) {
                output.append(line).append('\n');
            }

            return "exitCode=" + exitCode + "\n" + output;

        } catch (Exception e) {
            return "Exception: " + e;
        }
    }
}