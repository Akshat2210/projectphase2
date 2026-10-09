"""Combine unprinted PDF bills, send them to Windows print, and keep an audit log.

Place this script in the folder that contains the bills, then run it. It uses
its own folder automatically. You can also supply a different folder:

    python print_bills.py
    python print_bills.py "C:\\Bills\\October"

Install the one required package first:

    py -m pip install pypdf
"""

from __future__ import annotations

import argparse
import importlib
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

LOG_FILE_NAME = "printed.json"
COMBINED_FILE_NAME = "_combined_bills_for_printing.pdf"


def ensure_pypdf_is_installed():
    """Import pypdf, installing it automatically the first time if needed."""
    try:
        return importlib.import_module("pypdf")
    except ImportError:
        print("The required pypdf package is not installed. Installing it now...")
        try:
            subprocess.run(
                [sys.executable, "-m", "pip", "install", "pypdf>=5.0.0"],
                check=True,
            )
        except (OSError, subprocess.CalledProcessError) as error:
            raise RuntimeError(
                "Automatic installation failed. Check your internet connection and Python installation, "
                "then run: python -m pip install pypdf"
            ) from error
        try:
            return importlib.import_module("pypdf")
        except ImportError as error:
            raise RuntimeError("pypdf was installed but could not be loaded.") from error


def load_printed_log(log_path: Path) -> dict:
    """Load the audit log and retain compatibility with a missing log."""
    if not log_path.exists():
        return {"printed_files": []}

    try:
        with log_path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except (json.JSONDecodeError, OSError) as error:
        raise RuntimeError(
            f"Cannot read {log_path.name}. Fix or rename it before printing. Details: {error}"
        ) from error

    if not isinstance(data, dict) or not isinstance(data.get("printed_files", []), list):
        raise RuntimeError(
            f"{log_path.name} must contain an object with a 'printed_files' list."
        )
    return data


def save_printed_log(log_path: Path, log: dict) -> None:
    """Atomically save the log so an interrupted write does not corrupt it."""
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", delete=False, dir=log_path.parent, suffix=".tmp"
    ) as temp_file:
        json.dump(log, temp_file, indent=2, ensure_ascii=False)
        temp_file.write("\n")
        temp_name = temp_file.name
    Path(temp_name).replace(log_path)


def find_unprinted_pdfs(folder: Path, printed_names: set[str]) -> list[Path]:
    """Find only PDFs in this folder, excluding the temporary combined PDF."""
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
            reader = pypdf.PdfReader(str(pdf_path))
            if reader.is_encrypted:
                raise RuntimeError(f"{pdf_path.name} is password-protected and cannot be combined.")
            for page in reader.pages:
                writer.add_page(page)
        with output_path.open("wb") as output_file:
            writer.write(output_file)
    finally:
        writer.close()


def request_windows_print(pdf_path: Path) -> None:
    """Ask Windows' default PDF application to print the combined document."""
    if os.name != "nt":
        raise RuntimeError("This script currently uses the Windows print command.")
    try:
        os.startfile(str(pdf_path), "print")  # type: ignore[attr-defined]
    except OSError as error:
        raise RuntimeError(
            "Windows could not open the print page. Set a default PDF app and printer, then try again. "
            f"Details: {error}"
        ) from error


def confirm_printing() -> bool:
    print()
    answer = input(
        "When the print dialog has finished and the job was submitted, type YES and press Enter. "
        "Type anything else to keep the combined PDF and leave the files unlogged: "
    )
    return answer.strip().upper() == "YES"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Combine unprinted PDFs, open the Windows print page, and record submitted bills."
    )
    parser.add_argument(
        "folder",
        nargs="?",
        default=Path(__file__).resolve().parent,
        type=Path,
        help="Folder containing bill PDFs (default: the folder containing this script).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="List PDFs that would be printed without creating, printing, or logging anything.",
    )
    args = parser.parse_args()

    folder = args.folder.expanduser().resolve()
    if not folder.is_dir():
        print(f"Folder not found: {folder}", file=sys.stderr)
        return 2

    log_path = folder / LOG_FILE_NAME
    combined_path = folder / COMBINED_FILE_NAME
    try:
        log = load_printed_log(log_path)
        printed_names = {
            item["file"] if isinstance(item, dict) else item
            for item in log["printed_files"]
            if isinstance(item, str) or (isinstance(item, dict) and isinstance(item.get("file"), str))
        }
        source_files = find_unprinted_pdfs(folder, printed_names)
    except RuntimeError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    if not source_files:
        print("No new PDF bills to print.")
        return 0

    print("PDF bills to print:")
    for path in source_files:
        print(f"  - {path.name}")

    if args.dry_run:
        print(f"\nDry run: {len(source_files)} file(s) would be combined and printed.")
        return 0

    if combined_path.exists():
        print(
            f"\nA previous temporary combined file already exists: {combined_path.name}\n"
            "It was not deleted automatically. Review or remove it, then run the script again.",
            file=sys.stderr,
        )
        return 1

    try:
        pypdf = ensure_pypdf_is_installed()
        combine_pdfs(source_files, combined_path, pypdf)
        print(f"\nCreated {combined_path.name}. Opening the Windows print page now...")
        request_windows_print(combined_path)

        if not confirm_printing():
            print(f"Not logged. The combined file was kept at: {combined_path}")
            return 0

        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        log["printed_files"].extend(
            {"file": path.name, "printed_at_utc": timestamp} for path in source_files
        )
        save_printed_log(log_path, log)
        combined_path.unlink()
        print(f"Recorded {len(source_files)} printed bill(s) in {log_path.name}.")
        print("Deleted the temporary combined PDF.")
        return 0
    except Exception as error:
        print(f"Error: {error}", file=sys.stderr)
        if combined_path.exists():
            print(f"The combined file was kept for review: {combined_path}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
