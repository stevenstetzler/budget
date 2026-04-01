import argparse
import datetime
import os

import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font
from openpyxl.utils import get_column_letter


def _to_bool(val):
    """Coerce truthy/falsy values (bool, str, int) to Python bool."""
    if isinstance(val, bool):
        return val
    if isinstance(val, str):
        return val.strip().lower() == "true"
    return bool(val)


def _write_summary_sheet(ws, most_recent_month, most_recent_year):
    """
    Write the Summary tab layout.

    Layout
    ------
    A1:A2   "Date"              (merged, bold, centered)
    B1:C2   <selected month>    (merged, date value formatted as "MMM YYYY")
    D1:D2   "Cash Flow"         (merged, bold, centered)
    E1      "Amount"            (bold)
    F1      "Fraction"          (bold)
    G1      "Budget"            (bold)
    H1      "Fraction"          (bold)
    E2      =$B$4-E4
    F2      =ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT(ADDRESS(ROW(),COLUMN()-1))/$B$4),1,1)
    G2      =$B$4-G4
    H2      =ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT(ADDRESS(ROW(),COLUMN()-1))/$B$4),1,1)
    A3:C3   "Income"            (merged, bold, centered)
    D3:H3   "Expenses"          (merged, bold, centered)
    A4      "Total"             (bold)
    B4      =SUM(B6:B)
    D4      "Total"             (bold)
    E4      =SUM(E6:E102)
    F4      =E4/$B$4
    G4      =SUM(G6:G102)
    H4      =G4/$B$4
    A5-H5   column headers      (all bold)
    A6:H105 per-row formulas referencing the selected receipts sheet
    """
    bold = Font(bold=True)
    center = Alignment(horizontal="center", vertical="center")

    # --- Row 1 / Row 2 merged headers ---

    # A1:A2 "Date"
    ws.merge_cells(start_row=1, start_column=1, end_row=2, end_column=1)
    ws.cell(row=1, column=1).value = "Date"
    ws.cell(row=1, column=1).font = bold
    ws.cell(row=1, column=1).alignment = center

    # B1:C2 — date value for the selected month
    ws.merge_cells(start_row=1, start_column=2, end_row=2, end_column=3)
    ws.cell(row=1, column=2).value = datetime.date(most_recent_year, most_recent_month, 1)
    ws.cell(row=1, column=2).number_format = "MMM YYYY"
    ws.cell(row=1, column=2).alignment = center

    # D1:D2 "Cash Flow"
    ws.merge_cells(start_row=1, start_column=4, end_row=2, end_column=4)
    ws.cell(row=1, column=4).value = "Cash Flow"
    ws.cell(row=1, column=4).font = bold
    ws.cell(row=1, column=4).alignment = center

    # E1, F1, G1, H1 — column headers
    for col, text in [(5, "Amount"), (6, "Fraction"), (7, "Budget"), (8, "Fraction")]:
        ws.cell(row=1, column=col).value = text
        ws.cell(row=1, column=col).font = bold

    # Row 2 — cash flow formulas
    ws.cell(row=2, column=5).value = "=$B$4-E4"
    ws.cell(row=2, column=6).value = (
        "=ARRAY_CONSTRAIN(ARRAYFORMULA("
        "INDIRECT(ADDRESS(ROW(),COLUMN()-1))/$B$4), 1, 1)"
    )
    ws.cell(row=2, column=7).value = "=$B$4-G4"
    ws.cell(row=2, column=8).value = (
        "=ARRAY_CONSTRAIN(ARRAYFORMULA("
        "INDIRECT(ADDRESS(ROW(),COLUMN()-1))/$B$4), 1, 1)"
    )

    # --- Row 3 — section labels ---
    ws.merge_cells(start_row=3, start_column=1, end_row=3, end_column=3)
    ws.cell(row=3, column=1).value = "Income"
    ws.cell(row=3, column=1).font = bold
    ws.cell(row=3, column=1).alignment = center

    ws.merge_cells(start_row=3, start_column=4, end_row=3, end_column=8)
    ws.cell(row=3, column=4).value = "Expenses"
    ws.cell(row=3, column=4).font = bold
    ws.cell(row=3, column=4).alignment = center

    # --- Row 4 — totals ---
    ws.cell(row=4, column=1).value = "Total"
    ws.cell(row=4, column=1).font = bold
    ws.cell(row=4, column=2).value = "=SUM(B6:B)"
    ws.cell(row=4, column=4).value = "Total"
    ws.cell(row=4, column=4).font = bold
    ws.cell(row=4, column=5).value = "=SUM(E6:E102)"
    ws.cell(row=4, column=6).value = "=E4/$B$4"
    ws.cell(row=4, column=7).value = "=SUM(G6:G102)"
    ws.cell(row=4, column=8).value = "=G4/$B$4"

    # --- Row 5 — column headers ---
    for col, text in [
        (1, "Source"), (2, "Amount"), (3, "Fraction"),
        (4, "Item"), (5, "Amount"), (6, "Fraction"), (7, "Budget"), (8, "Fraction"),
    ]:
        ws.cell(row=5, column=col).value = text
        ws.cell(row=5, column=col).font = bold

    # --- Rows 6–105 — per-row formulas ---
    # Income (cols A–C): individual income transactions from the receipts sheet
    # Expenses (cols D–H): one row per expense category (name, total, %, budget, %)
    _ref = (
        "\"'\"&MONTH($B$1)&\" \"&YEAR($B$1)&\" Receipts'!\""
    )
    for row in range(6, 106):
        ws.cell(row=row, column=1).value = (
            f"=ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT({_ref}"
            f"&ADDRESS(ROW(),1))), 1, 1)"
        )
        ws.cell(row=row, column=2).value = (
            f"=ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT({_ref}"
            f"&ADDRESS(ROW(),2))), 1, 1)"
        )
        ws.cell(row=row, column=3).value = f"=B{row}/$B$4"
        ws.cell(row=row, column=4).value = (
            f"=ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT({_ref}"
            f"&ADDRESS(1, (ROW()-ROW($D$6))*2+1+2))), 1, 1)"
        )
        ws.cell(row=row, column=5).value = (
            f"=ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT({_ref}"
            f"&ADDRESS(2, (ROW()-ROW($D$6))*2+1+1+2))), 1, 1)"
        )
        ws.cell(row=row, column=6).value = f"=E{row}/$B$4"
        ws.cell(row=row, column=7).value = (
            f"=ARRAY_CONSTRAIN(ARRAYFORMULA(INDIRECT({_ref}"
            f"&ADDRESS(3, (ROW()-ROW($D$6))*2+1+1+2))), 1, 1)"
        )
        ws.cell(row=row, column=8).value = f"=G{row}/$B$4"


def _write_category_pair(ws, col_pair_idx, cat, cat_df, bold, center):
    """Write one category column pair (rows 1–5 structure + transactions) onto ws."""
    desc_col = col_pair_idx * 2 + 1
    amt_col = col_pair_idx * 2 + 2
    amt_col_letter = get_column_letter(amt_col)

    # Row 1: category name merged across both columns (bold, centered)
    ws.cell(row=1, column=desc_col).value = cat
    ws.cell(row=1, column=desc_col).font = bold
    ws.cell(row=1, column=desc_col).alignment = center
    ws.merge_cells(
        start_row=1, start_column=desc_col,
        end_row=1, end_column=amt_col,
    )

    # Row 2: "Total" | SUM formula
    ws.cell(row=2, column=desc_col).value = "Total"
    ws.cell(row=2, column=desc_col).font = bold
    ws.cell(row=2, column=amt_col).value = (
        f"=SUM({amt_col_letter}6:{amt_col_letter}1001)"
    )

    # Row 3: "Budget" | 0
    ws.cell(row=3, column=desc_col).value = "Budget"
    ws.cell(row=3, column=desc_col).font = bold
    ws.cell(row=3, column=amt_col).value = 0

    # Row 4: empty (spacer)

    # Row 5: "Note" | "Amount" column headers (both bold)
    ws.cell(row=5, column=desc_col).value = "Note"
    ws.cell(row=5, column=desc_col).font = bold
    ws.cell(row=5, column=amt_col).value = "Amount"
    ws.cell(row=5, column=amt_col).font = bold

    # Rows 6+: transactions
    for tx_idx, tx_row in cat_df.iterrows():
        excel_row = 6 + tx_idx
        ws.cell(row=excel_row, column=desc_col).value = tx_row["description"]
        ws.cell(row=excel_row, column=amt_col).value = tx_row["amount"]


def export_to_excel(input_file_path, output_file_path="budget_export.xlsx"):
    """
    Convert a CSV or JSON file (as produced by parse_receipts.py) back into
    the legacy Excel spreadsheet format.

    Input columns expected: date, category, description, amount, isPositive.
    The isPositive column determines whether a transaction is income (True) or
    an expense (False).  All income transactions are grouped into a single
    "Income" category that always occupies the first column pair on each sheet.

    The date column may use any format pandas can parse, including
    "M/D/YYYY" (e.g. "3/1/2026"), "YYYY-MM-DD" (e.g. "2026-03-01"),
    or datetime/Timestamp objects that pandas produces when reading JSON.

    Output Excel structure:
    - Sheet 1: "Summary" with income/expense overview and formulas
    - One additional sheet per month, named "M YYYY Receipts" (e.g. "3 2026 Receipts"),
      ordered latest-month-first.  Each category occupies a pair of columns:
      - Column pair 1: "Income" (all isPositive=True transactions)
      - Remaining pairs: one per unique expense category (isPositive=False)
      Each pair follows the structure:
      - Row 1: category name, merged across both columns, bold, centered
      - Row 2: "Total" (bold) | =SUM formula for the amount column
      - Row 3: "Budget" (bold) | budgeted amount (0 when not supplied)
      - Row 4: empty
      - Row 5: "Note" (bold) | "Amount" (bold)  — column headers
      - Row 6+: description (left col) / amount (right col) per transaction

    Parameters
    ----------
    input_file_path : str
        Path to the input CSV (.csv) or JSON (.json) file.
    output_file_path : str
        Path for the generated Excel workbook. Defaults to "budget_export.xlsx".

    Returns
    -------
    str
        The resolved path to the written Excel file.
    """
    ext = os.path.splitext(input_file_path)[1].lower()
    if ext == ".csv":
        df = pd.read_csv(input_file_path)
    elif ext == ".json":
        df = pd.read_json(input_file_path)
    else:
        raise ValueError(f"Unsupported file type: '{ext}'. Use .csv or .json.")

    required_columns = {"date", "category", "description", "amount"}
    missing = required_columns - set(df.columns)
    if missing:
        raise ValueError(f"Input file is missing required columns: {missing}")

    # Parse (month, year) from dates.
    # Accepts datetime/Timestamp objects as well as any date string that
    # pandas can parse (e.g. "M/D/YYYY", "YYYY-MM-DD", "MM-DD-YYYY", …).
    def _extract_month_year(date_val):
        if hasattr(date_val, "month") and hasattr(date_val, "year"):
            return int(date_val.month), int(date_val.year)
        try:
            ts = pd.to_datetime(str(date_val))
            return ts.month, ts.year
        except Exception:
            raise ValueError(
                f"Unrecognized date format '{date_val}'. "
                "Supported formats include 'M/D/YYYY' and 'YYYY-MM-DD'."
            )

    df["_month"], df["_year"] = zip(*df["date"].apply(_extract_month_year))

    bold = Font(bold=True)
    center = Alignment(horizontal="center")

    # One sheet per month, ordered in descending order (latest month first)
    month_groups = df.groupby(["_year", "_month"], sort=True)
    sorted_months = sorted(
        month_groups, key=lambda x: (x[0][0], x[0][1]), reverse=True
    )

    # Determine most recent month for the Summary tab
    if sorted_months:
        (most_recent_year, most_recent_month) = sorted_months[0][0]
    else:
        today = datetime.date.today()
        most_recent_year, most_recent_month = today.year, today.month

    wb = Workbook()
    ws_summary = wb.active
    ws_summary.title = "Summary"
    _write_summary_sheet(ws_summary, most_recent_month, most_recent_year)

    for (year, month), group in sorted_months:
        sheet_name = f"{month} {year} Receipts"
        ws = wb.create_sheet(title=sheet_name)

        # Separate income (isPositive=True) and expense transactions.
        # If no isPositive column is present, treat all as expenses.
        if "isPositive" in group.columns:
            income_mask = group["isPositive"].apply(_to_bool)
        else:
            income_mask = pd.Series([False] * len(group), index=group.index)

        income_df = group[income_mask].reset_index(drop=True)
        expense_df = group[~income_mask].reset_index(drop=True)

        # Expense categories in first-seen insertion order
        expense_cats = list(dict.fromkeys(expense_df["category"].tolist()))

        # "Income" is always the first column pair; expense categories follow
        col_pairs = [("Income", income_df)] + [
            (cat, expense_df[expense_df["category"] == cat].reset_index(drop=True))
            for cat in expense_cats
        ]

        for col_pair_idx, (cat, cat_df) in enumerate(col_pairs):
            _write_category_pair(ws, col_pair_idx, cat, cat_df, bold, center)

    wb.save(output_file_path)
    return os.path.realpath(output_file_path)


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Convert a CSV or JSON budget file into the legacy Excel spreadsheet format. "
            "This is the inverse of parse_receipts.py."
        )
    )
    parser.add_argument(
        "input",
        help="Path to the input CSV or JSON file.",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="budget_export.xlsx",
        help="Path for the output Excel file (default: budget_export.xlsx).",
    )
    args = parser.parse_args()

    output_path = export_to_excel(args.input, args.output)
    print(f"Exported to: {output_path}")


if __name__ == "__main__":
    main()
