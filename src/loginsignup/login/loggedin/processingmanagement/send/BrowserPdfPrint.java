package loginsignup.login.loggedin.processingmanagement.send;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Opens a browser PDF viewer and requests its print dialog. Never submits a print job. */
final class BrowserPdfPrint {
    private BrowserPdfPrint() { }

    static void openPrintDialog(Path pdf) throws IOException, InterruptedException {
        if (!Files.isRegularFile(pdf)) throw new IOException("The saved PDF could not be found: " + pdf);
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new IOException("Automatic browser print-dialog opening is currently supported on Windows only.");
        }
        String encodedScript = java.util.Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encodedScript);
        builder.redirectErrorStream(true);
        // Pass data separately from executable script text, including names containing quotes or spaces.
        builder.environment().put("SEND_RECEIPT_PDF", pdf.toAbsolutePath().toString());
        builder.environment().put("SEND_RECEIPT_TITLE", pdf.getFileName().toString());
        Process helper = builder.start();
        helper.getOutputStream().close();
        if (!helper.waitFor(35, TimeUnit.SECONDS)) {
            helper.destroyForcibly();
            throw new IOException("The browser did not become ready. Open the saved PDF and press Ctrl+P.");
        }
        String output = new String(helper.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (helper.exitValue() != 0 || !output.contains("PRINT_DIALOG_REQUESTED")) {
            throw new IOException("Could not request the browser print dialog. Open the saved PDF and press Ctrl+P."
                    + (output.isEmpty() ? "" : "\n" + output));
        }
    }

    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            try {
              $pdf = $env:SEND_RECEIPT_PDF
              $title = $env:SEND_RECEIPT_TITLE
              $choice = (Get-ItemProperty -LiteralPath 'HKCU:\\Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https\\UserChoice' -ErrorAction SilentlyContinue).ProgId
              $names = if ($choice -like '*Chrome*') { @('Google\\Chrome\\Application\\chrome.exe','Microsoft\\Edge\\Application\\msedge.exe') } else { @('Microsoft\\Edge\\Application\\msedge.exe','Google\\Chrome\\Application\\chrome.exe') }
              $browser = $null
              foreach ($name in $names) {
                foreach ($root in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
                  if ($root) {
                    $candidate = Join-Path $root $name
                    if (Test-Path -LiteralPath $candidate) { $browser = $candidate; break }
                  }
                }
                if ($browser) { break }
              }
              if (-not $browser) { throw 'Microsoft Edge or Google Chrome was not found.' }
              Add-Type -TypeDefinition @'
            using System;
            using System.Text;
            using System.Diagnostics;
            using System.Runtime.InteropServices;
            public static class SendPdfWindow {
              public delegate bool EnumProc(IntPtr h, IntPtr p);
              [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc callback, IntPtr p);
              [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
              [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
              [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int command);
              [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
              public static IntPtr Find(string title) {
                IntPtr found = IntPtr.Zero;
                EnumWindows(delegate(IntPtr h, IntPtr p) {
                  if (!IsWindowVisible(h)) return true;
                  StringBuilder text = new StringBuilder(2048);
                  GetWindowText(h, text, text.Capacity);
                  if (text.ToString().IndexOf(title, StringComparison.OrdinalIgnoreCase) < 0) return true;
                  uint pid; GetWindowThreadProcessId(h, out pid);
                  try {
                    string name = Process.GetProcessById((int)pid).ProcessName;
                    if (name == "msedge" || name == "chrome") { found = h; return false; }
                  } catch { }
                  return true;
                }, IntPtr.Zero);
                return found;
              }
            }
            '@
              $uri = ([Uri]$pdf).AbsoluteUri
              Start-Process -FilePath $browser -ArgumentList @('--new-window', ('"' + $uri + '"')) -WindowStyle Normal | Out-Null
              $until = [DateTime]::UtcNow.AddSeconds(20)
              $window = [IntPtr]::Zero
              while ([DateTime]::UtcNow -lt $until) {
                $window = [SendPdfWindow]::Find($title)
                if ($window -ne [IntPtr]::Zero) { break }
                Start-Sleep -Milliseconds 250
              }
              if ($window -eq [IntPtr]::Zero) { throw 'The PDF browser window could not be identified. Press Ctrl+P in the PDF window.' }
              [SendPdfWindow]::ShowWindow($window, 9) | Out-Null
              [SendPdfWindow]::SetForegroundWindow($window) | Out-Null
              Start-Sleep -Milliseconds 1000
              if ([SendPdfWindow]::GetForegroundWindow() -ne $window) { throw 'The PDF window is not active; Ctrl+P was not sent. Select the PDF window and press Ctrl+P.' }
              $shell = New-Object -ComObject WScript.Shell
              $shell.SendKeys('^p')
              Write-Output 'PRINT_DIALOG_REQUESTED'
              exit 0
            } catch {
              Write-Output $_.Exception.Message
              exit 1
            }
            """;
}
