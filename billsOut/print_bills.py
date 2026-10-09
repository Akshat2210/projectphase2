"""GUI bill printer for Windows.

Place this script in the folder containing bill PDFs and run it.

Features:
- Uses the script's own folder by default.
- Lets you browse to another bill folder.
- Shows only PDFs that have not already been logged as printed.
- Lets you select which bills to print.
- Combines selected PDFs into one temporary PDF.
- Opens the PDF and sends Ctrl+P to the default PDF viewer.
- Logs bills only after you confirm the print job was submitted.
- Keeps an audit trail in printed.json.

pypdf is installed automatically if it is missing.
"""

from __future__ import annotations

import importlib
import json
import os
import subprocess
import sys
import tempfile
import time
import ctypes
import threading
import tkinter as tk
from datetime import datetime, timezone
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

LOG_FILE_NAME = "printed.json"
COMBINED_FILE_NAME = "_combined_bills_for_printing.pdf"


def ensure_pypdf_is_installed():
    """Import pypdf, installing it automatically if needed."""
    try:
        return importlib.import_module("pypdf")
    except ImportError:
        try:
            subprocess.run(
                [sys.executable, "-m", "pip", "install", "pypdf>=5.0.0"],
                check=True,
            )
        except (OSError, subprocess.CalledProcessError) as error:
            raise RuntimeError(
                "Automatic installation of pypdf failed.\n\n"
                "Check your internet connection and Python installation, then run:\n"
                "python -m pip install pypdf"
            ) from error

        try:
            return importlib.import_module("pypdf")
        except ImportError as error:
            raise RuntimeError(
                "pypdf was installed but could not be loaded."
            ) from error


def load_printed_log(log_path: Path) -> dict:
    """Load the audit log."""
    if not log_path.exists():
        return {"printed_files": []}

    try:
        with log_path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except (json.JSONDecodeError, OSError) as error:
        raise RuntimeError(
            f"Cannot read {log_path.name}.\n\n"
            f"Fix or rename it before printing.\n\nDetails: {error}"
        ) from error

    if not isinstance(data, dict) or not isinstance(
        data.get("printed_files", []), list
    ):
        raise RuntimeError(
            f"{log_path.name} must contain an object with a 'printed_files' list."
        )

    return data


def save_printed_log(log_path: Path, log: dict) -> None:
    """Atomically save the audit log."""
    with tempfile.NamedTemporaryFile(
        "w",
        encoding="utf-8",
        delete=False,
        dir=log_path.parent,
        suffix=".tmp",
    ) as temp_file:
        json.dump(log, temp_file, indent=2, ensure_ascii=False)
        temp_file.write("\n")
        temp_name = temp_file.name

    Path(temp_name).replace(log_path)


def get_printed_names(log: dict) -> set[str]:
    names = set()

    for item in log.get("printed_files", []):
        if isinstance(item, str):
            names.add(item)
        elif isinstance(item, dict) and isinstance(item.get("file"), str):
            names.add(item["file"])

    return names


def find_unprinted_pdfs(folder: Path, printed_names: set[str]) -> list[Path]:
    """Find PDFs that have not been logged as printed."""
    combined_path = folder / COMBINED_FILE_NAME

    return sorted(
        (
            path
            for path in folder.glob("*.pdf")
            if path.resolve() != combined_path.resolve()
            and path.name not in printed_names
        ),
        key=lambda path: path.name.casefold(),
    )


def combine_pdfs(source_files: list[Path], output_path: Path, pypdf) -> None:
    writer = pypdf.PdfWriter()

    try:
        for pdf_path in source_files:
            try:
                reader = pypdf.PdfReader(str(pdf_path))
            except Exception as error:
                raise RuntimeError(
                    f"Could not read PDF:\n{pdf_path.name}\n\n{error}"
                ) from error

            if reader.is_encrypted:
                raise RuntimeError(
                    f"{pdf_path.name} is password-protected and cannot be combined."
                )

            for page in reader.pages:
                writer.add_page(page)

        with output_path.open("wb") as output_file:
            writer.write(output_file)

    finally:
        writer.close()


def open_windows_print_dialog(pdf_path: Path) -> None:
    """Open PDF in default viewer and send Ctrl+P."""
    if os.name != "nt":
        raise RuntimeError(
            "Automatic print-dialog opening is currently supported only on Windows."
        )

    try:
        os.startfile(str(pdf_path), "open")  # type: ignore[attr-defined]
    except OSError as error:
        raise RuntimeError(
            "Windows could not open the PDF.\n\n"
            "Set a default PDF application and try again.\n\n"
            f"Details: {error}"
        ) from error

    # Give the PDF viewer time to open and receive focus.
    time.sleep(1.5)

    user32 = ctypes.windll.user32

    VK_CONTROL = 0x11
    VK_P = 0x50
    KEYEVENTF_KEYUP = 0x0002

    user32.keybd_event(VK_CONTROL, 0, 0, 0)
    user32.keybd_event(VK_P, 0, 0, 0)
    user32.keybd_event(VK_P, 0, KEYEVENTF_KEYUP, 0)
    user32.keybd_event(VK_CONTROL, 0, KEYEVENTF_KEYUP, 0)


class BillPrinterGUI(tk.Tk):
    def __init__(self):
        super().__init__()

        self.title("Bill PDF Printer")
        self.geometry("900x620")
        self.minsize(760, 500)

        try:
            self.state("zoomed")
        except tk.TclError:
            pass

        self.folder = Path(__file__).resolve().parent
        self.current_files: list[Path] = []
        self.busy = False

        self._configure_style()
        self._build_gui()
        self.refresh_files()

    def _configure_style(self):
        style = ttk.Style(self)

        if "vista" in style.theme_names():
            style.theme_use("vista")

        style.configure("Title.TLabel", font=("Segoe UI", 20, "bold"))
        style.configure("Heading.TLabel", font=("Segoe UI", 11, "bold"))
        style.configure("Info.TLabel", font=("Segoe UI", 10))
        style.configure("Print.TButton", font=("Segoe UI", 11, "bold"), padding=10)
        style.configure("TButton", padding=6)

    def _build_gui(self):
        main = ttk.Frame(self, padding=20)
        main.pack(fill="both", expand=True)

        ttk.Label(
            main,
            text="Bill PDF Printer",
            style="Title.TLabel",
        ).pack(anchor="w")

        ttk.Label(
            main,
            text="Select unprinted bills, combine them, and open the Windows print dialog.",
            style="Info.TLabel",
        ).pack(anchor="w", pady=(3, 18))

        # Folder row
        folder_frame = ttk.Frame(main)
        folder_frame.pack(fill="x", pady=(0, 12))

        ttk.Label(
            folder_frame,
            text="Bill folder:",
            style="Heading.TLabel",
        ).pack(side="left")

        self.folder_var = tk.StringVar(value=str(self.folder))

        self.folder_entry = ttk.Entry(
            folder_frame,
            textvariable=self.folder_var,
        )
        self.folder_entry.pack(side="left", fill="x", expand=True, padx=10)

        ttk.Button(
            folder_frame,
            text="Browse...",
            command=self.browse_folder,
        ).pack(side="left")

        ttk.Button(
            folder_frame,
            text="Refresh",
            command=self.refresh_files,
        ).pack(side="left", padx=(8, 0))

        # Count / selection controls
        controls = ttk.Frame(main)
        controls.pack(fill="x", pady=(0, 8))

        self.count_var = tk.StringVar(value="0 unprinted bills")
        ttk.Label(
            controls,
            textvariable=self.count_var,
            style="Heading.TLabel",
        ).pack(side="left")

        ttk.Button(
            controls,
            text="Select All",
            command=self.select_all,
        ).pack(side="right")

        ttk.Button(
            controls,
            text="Clear Selection",
            command=self.clear_selection,
        ).pack(side="right", padx=(0, 8))

        # PDF list
        list_frame = ttk.Frame(main)
        list_frame.pack(fill="both", expand=True)

        self.tree = ttk.Treeview(
            list_frame,
            columns=("name", "size"),
            show="headings",
            selectmode="extended",
        )

        self.tree.heading("name", text="PDF Bill")
        self.tree.heading("size", text="Size")

        self.tree.column("name", width=650, anchor="w")
        self.tree.column("size", width=100, anchor="e", stretch=False)

        scrollbar = ttk.Scrollbar(
            list_frame,
            orient="vertical",
            command=self.tree.yview,
        )
        self.tree.configure(yscrollcommand=scrollbar.set)

        self.tree.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

        # Bottom actions
        bottom = ttk.Frame(main)
        bottom.pack(fill="x", pady=(14, 0))

        self.status_var = tk.StringVar(value="Ready")

        ttk.Label(
            bottom,
            textvariable=self.status_var,
        ).pack(side="left", fill="x", expand=True)

        ttk.Button(
            bottom,
            text="Printed History",
            command=self.show_history,
        ).pack(side="right", padx=(8, 0))

        self.print_button = ttk.Button(
            bottom,
            text="Combine && Print Selected",
            style="Print.TButton",
            command=self.start_print,
        )
        self.print_button.pack(side="right")

    def set_status(self, text: str):
        self.status_var.set(text)
        self.update_idletasks()

    def browse_folder(self):
        selected = filedialog.askdirectory(
            title="Select folder containing bill PDFs",
            initialdir=str(self.folder),
        )

        if not selected:
            return

        self.folder = Path(selected).resolve()
        self.folder_var.set(str(self.folder))
        self.refresh_files()

    def refresh_files(self):
        if self.busy:
            return

        typed_folder = Path(self.folder_var.get()).expanduser()

        try:
            folder = typed_folder.resolve()
        except OSError:
            folder = typed_folder

        if not folder.is_dir():
            messagebox.showerror(
                "Folder Not Found",
                f"The selected folder does not exist:\n\n{folder}",
                parent=self,
            )
            return

        self.folder = folder
        self.folder_var.set(str(folder))

        try:
            log = load_printed_log(folder / LOG_FILE_NAME)
            printed_names = get_printed_names(log)
            self.current_files = find_unprinted_pdfs(folder, printed_names)
        except Exception as error:
            messagebox.showerror(
                "Cannot Load Bills",
                str(error),
                parent=self,
            )
            return

        for item in self.tree.get_children():
            self.tree.delete(item)

        for index, path in enumerate(self.current_files):
            try:
                size = self.format_size(path.stat().st_size)
            except OSError:
                size = "Unknown"

            self.tree.insert(
                "",
                "end",
                iid=str(index),
                values=(path.name, size),
            )

        count = len(self.current_files)
        self.count_var.set(
            f"{count} unprinted bill{'s' if count != 1 else ''}"
        )

        if self.current_files:
            self.select_all()
            self.set_status("Ready to print.")
        else:
            self.set_status("No new PDF bills to print.")

    @staticmethod
    def format_size(size: int) -> str:
        if size < 1024:
            return f"{size} B"
        if size < 1024 * 1024:
            return f"{size / 1024:.1f} KB"
        return f"{size / (1024 * 1024):.1f} MB"

    def select_all(self):
        items = self.tree.get_children()
        self.tree.selection_set(items)

    def clear_selection(self):
        self.tree.selection_remove(self.tree.selection())

    def get_selected_files(self) -> list[Path]:
        selected = []

        for item_id in self.tree.selection():
            try:
                selected.append(self.current_files[int(item_id)])
            except (ValueError, IndexError):
                continue

        return selected

    def start_print(self):
        if self.busy:
            return

        selected_files = self.get_selected_files()

        if not selected_files:
            messagebox.showwarning(
                "Nothing Selected",
                "Select at least one PDF bill to print.",
                parent=self,
            )
            return

        combined_path = self.folder / COMBINED_FILE_NAME

        self.busy = True
        self.print_button.configure(state="disabled")
        self.set_status("Combining selected PDFs...")

        threading.Thread(
            target=self._prepare_print,
            args=(selected_files, combined_path),
            daemon=True,
        ).start()

    def _prepare_print(self, selected_files: list[Path], combined_path: Path):
        try:
            pypdf = ensure_pypdf_is_installed()
            combine_pdfs(selected_files, combined_path, pypdf)

            self.after(
                0,
                lambda: self._open_print_dialog(selected_files, combined_path),
            )

        except Exception as error:
            self.after(
                0,
                lambda err=error: self._print_failed(err, combined_path),
            )

    def _open_print_dialog(
        self,
        selected_files: list[Path],
        combined_path: Path,
    ):
        self.set_status(
            "Opening PDF viewer. Please do not click elsewhere for a moment..."
        )

        threading.Thread(
            target=self._launch_print_dialog,
            args=(selected_files, combined_path),
            daemon=True,
        ).start()

    def _launch_print_dialog(
        self,
        selected_files: list[Path],
        combined_path: Path,
    ):
        try:
            open_windows_print_dialog(combined_path)

            self.after(
                0,
                lambda: self._confirm_print(selected_files, combined_path),
            )

        except Exception as error:
            self.after(
                0,
                lambda err=error: self._print_failed(err, combined_path),
            )

    def _confirm_print(
        self,
        selected_files: list[Path],
        combined_path: Path,
    ):
        self.set_status("Waiting for print confirmation...")

        confirmed = messagebox.askyesno(
            "Confirm Printing",
            "Has the print job been successfully submitted?\n\n"
            "Choose YES only after you have clicked Print in the print dialog.\n\n"
            "YES  → mark these bills as printed\n"
            "NO   → do not log them",
            parent=self,
        )

        if not confirmed:
            self.busy = False
            self.print_button.configure(state="normal")
            self.set_status(
                f"Not logged. Combined PDF kept as {COMBINED_FILE_NAME}."
            )

            messagebox.showinfo(
                "Not Logged",
                "No bills were marked as printed.\n\n"
                f"The combined PDF was kept here:\n{combined_path}",
                parent=self,
            )
            return

        try:
            log_path = self.folder / LOG_FILE_NAME
            log = load_printed_log(log_path)

            timestamp = (
                datetime.now(timezone.utc)
                .isoformat()
                .replace("+00:00", "Z")
            )

            log["printed_files"].extend(
                {
                    "file": path.name,
                    "printed_at_utc": timestamp,
                }
                for path in selected_files
            )

            save_printed_log(log_path, log)

            count = len(selected_files)

            self.busy = False
            self.print_button.configure(state="normal")

            messagebox.showinfo(
                "Printing Recorded",
                f"{count} bill{'s' if count != 1 else ''} "
                f"recorded in {LOG_FILE_NAME}.",
                parent=self,
            )

            self.refresh_files()

        except Exception as error:
            self.busy = False
            self.print_button.configure(state="normal")
            self.set_status("Error while saving print history.")

            messagebox.showerror(
                "Logging Error",
                "The print may have been submitted, but the audit log "
                f"could not be updated.\n\n{error}\n\n"
                "The combined PDF has been kept for review.",
                parent=self,
            )

    def _print_failed(self, error: Exception, combined_path: Path):
        self.busy = False
        self.print_button.configure(state="normal")
        self.set_status("Printing failed.")

        extra = ""

        if combined_path.exists():
            extra = (
                f"\n\nThe combined PDF was kept for review:\n{combined_path}"
            )

        messagebox.showerror(
            "Print Error",
            f"{error}{extra}",
            parent=self,
        )

    def reprint_history_bill(self, history_tree):
        """Reprint one or more selected history items without changing the audit log."""
        if self.busy:
            messagebox.showinfo(
                "Printer Busy",
                "Another print operation is already in progress.",
                parent=self,
            )
            return "break"

        selection = history_tree.selection()
        if not selection:
            return "break"

        # Preserve the order in which rows appear in the history table.
        selected_ids = set(selection)
        ordered_ids = [
            item_id
            for item_id in history_tree.get_children()
            if item_id in selected_ids
        ]

        filenames = []
        seen = set()

        for item_id in ordered_ids:
            values = history_tree.item(item_id, "values")
            if not values:
                continue

            filename = str(values[0])
            if filename and filename not in seen:
                filenames.append(filename)
                seen.add(filename)

        if not filenames:
            return "break"

        pdf_paths = [self.folder / filename for filename in filenames]
        missing = [path.name for path in pdf_paths if not path.is_file()]

        if missing:
            preview = "\n".join(f"• {name}" for name in missing[:15])
            if len(missing) > 15:
                preview += f"\n...and {len(missing) - 15} more"

            messagebox.showerror(
                "PDF Not Found",
                "The following original PDF bill(s) could not be found:\n\n"
                f"{preview}\n\n"
                "Nothing was sent to print.",
                parent=self,
            )
            return "break"

        count = len(pdf_paths)

        if count == 1:
            description = pdf_paths[0].name
        else:
            description = f"{count} selected bills"

        answer = messagebox.askyesno(
            "Reprint Bills",
            f"Reprint {description}?\n\n"
            "The selected PDFs will be combined into one print job.\n\n"
            "This reprint will NOT create new entries in printed.json.",
            parent=self,
        )
        if not answer:
            return "break"

        combined_path = self.folder / COMBINED_FILE_NAME

        self.busy = True
        self.print_button.configure(state="disabled")
        self.set_status(
            f"Preparing reprint of {count} bill{'s' if count != 1 else ''}..."
        )

        threading.Thread(
            target=self._prepare_reprint,
            args=(pdf_paths, combined_path),
            daemon=True,
        ).start()

        return "break"

    def _prepare_reprint(self, pdf_paths: list[Path], combined_path: Path):
        try:
            pypdf = ensure_pypdf_is_installed()
            combine_pdfs(pdf_paths, combined_path, pypdf)

            self.after(
                0,
                lambda: self._open_reprint_dialog(pdf_paths, combined_path),
            )
        except Exception as error:
            self.after(
                0,
                lambda err=error: self._print_failed(err, combined_path),
            )

    def _open_reprint_dialog(self, pdf_paths: list[Path], combined_path: Path):
        self.set_status(
            "Opening PDF viewer. Please do not click elsewhere for a moment..."
        )

        threading.Thread(
            target=self._launch_reprint_dialog,
            args=(pdf_paths, combined_path),
            daemon=True,
        ).start()

    def _launch_reprint_dialog(self, pdf_paths: list[Path], combined_path: Path):
        try:
            open_windows_print_dialog(combined_path)
            self.after(
                0,
                lambda: self._confirm_reprint(pdf_paths, combined_path),
            )
        except Exception as error:
            self.after(
                0,
                lambda err=error: self._print_failed(err, combined_path),
            )

    def _confirm_reprint(self, pdf_paths: list[Path], combined_path: Path):
        self.set_status("Waiting for reprint confirmation...")

        confirmed = messagebox.askyesno(
            "Confirm Reprint",
            "Has the reprint job been successfully submitted?\n\n"
            "YES  → confirm the print was submitted\n"
            "NO   → leave it unconfirmed\n\n"
            "The combined PDF is kept and automatically overwritten next time.\n"
            "The printed history will not be changed.",
            parent=self,
        )

        if confirmed:
            count = len(pdf_paths)
            self.set_status(
                f"Reprint submitted: {count} bill{'s' if count != 1 else ''}"
            )
        else:
            self.set_status(
                f"Reprint not confirmed. Combined PDF kept as {COMBINED_FILE_NAME}."
            )

        self.busy = False
        self.print_button.configure(state="normal")

    def show_history(self):
        try:
            log = load_printed_log(self.folder / LOG_FILE_NAME)
        except Exception as error:
            messagebox.showerror(
                "Cannot Read History",
                str(error),
                parent=self,
            )
            return

        history = log.get("printed_files", [])

        window = tk.Toplevel(self)
        window.title("Printed Bill History")
        window.geometry("760x500")
        window.transient(self)

        frame = ttk.Frame(window, padding=15)
        frame.pack(fill="both", expand=True)

        ttk.Label(
            frame,
            text=f"Printed History — {len(history)} record(s)",
            style="Heading.TLabel",
        ).pack(anchor="w")

        ttk.Label(
            frame,
            text="Ctrl-click or Shift-click to select multiple bills, then press Enter to reprint.",
        ).pack(anchor="w", pady=(2, 10))

        tree_frame = ttk.Frame(frame)
        tree_frame.pack(fill="both", expand=True)

        history_tree = ttk.Treeview(
            tree_frame,
            columns=("file", "date"),
            show="headings",
            selectmode="extended",
        )

        history_tree.heading("file", text="PDF Bill")
        history_tree.heading("date", text="Printed At (UTC)")

        history_tree.column("file", width=450, anchor="w")
        history_tree.column("date", width=240, anchor="w")

        scrollbar = ttk.Scrollbar(
            tree_frame,
            orient="vertical",
            command=history_tree.yview,
        )
        history_tree.configure(yscrollcommand=scrollbar.set)

        history_tree.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

        # Reprint a previously printed bill with Enter or double-click.
        history_tree.bind(
            "<Double-1>",
            lambda event: self.reprint_history_bill(history_tree),
        )
        history_tree.bind(
            "<Return>",
            lambda event: self.reprint_history_bill(history_tree),
        )

        for item in reversed(history):
            if isinstance(item, str):
                filename = item
                printed_at = ""
            elif isinstance(item, dict):
                filename = str(item.get("file", ""))
                printed_at = str(item.get("printed_at_utc", ""))
            else:
                continue

            history_tree.insert(
                "",
                "end",
                values=(filename, printed_at),
            )

        ttk.Button(
            frame,
            text="Close",
            command=window.destroy,
        ).pack(anchor="e", pady=(10, 0))


def main():
    app = BillPrinterGUI()
    app.mainloop()


if __name__ == "__main__":
    main()
