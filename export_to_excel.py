import argparse
import os

import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Font
from openpyxl.utils import get_column_letter


def export_to_excel(input_file_path, output_file_path="budget_export.xlsx"):
    """
    Convert a CSV or JSON file (as produced by parse_receipts.py) back into
    the legacy Excel spreadsheet format.

    Input columns expected: date, category, description, amount
    (isPositive is accepted in the input but not written to Excel;
    parse_receipts.py derives it from the category name on import)

    The date column may use any format pandas can parse, including
    "M/D/YYYY" (e.g. "3/1/2026"), "YYYY-MM-DD" (e.g. "2026-03-01"),
    or datetime/Timestamp objects that pandas produces when reading JSON.

    Output Excel structure:
    - Sheet 1: "Summary" (placeholder; parse_receipts.py skips the first sheet)
    - One additional sheet per month, named "M YYYY Receipts" (e.g. "3 2026 Receipts")
      Each category occupies a pair of columns (description / amount):
      - Row 1: category name, merged across both columns, bold
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

    wb = Workbook()
    # Rename the default sheet to "Summary" (parse_receipts.py skips sheet 0)
    wb.active.title = "Summary"

    # One sheet per month, ordered chronologically in ascending order
    month_groups = df.groupby(["_year", "_month"], sort=True)

    for (year, month), group in sorted(
        month_groups, key=lambda x: (x[0][0], x[0][1])
    ):
        sheet_name = f"{month} {year} Receipts"
        ws = wb.create_sheet(title=sheet_name)

        # Unique categories for this month (preserve insertion order)
        categories = list(dict.fromkeys(group["category"].tolist()))

        transactions_per_category = {
            cat: group[group["category"] == cat].reset_index(drop=True)
            for cat in categories
        }

        for col_pair, cat in enumerate(categories):
            # openpyxl uses 1-based row/column indices
            desc_col = col_pair * 2 + 1   # left column of this category pair
            amt_col = col_pair * 2 + 2    # right column of this category pair
            amt_col_letter = get_column_letter(amt_col)

            # Row 1: category name merged across both columns (bold)
            ws.cell(row=1, column=desc_col).value = cat
            ws.cell(row=1, column=desc_col).font = bold
            ws.merge_cells(
                start_row=1, start_column=desc_col,
                end_row=1, end_column=amt_col,
            )

            # Row 2: "Total" | SUM formula (transactions start at row 6;
            # end row 1001 is a large upper bound that covers any realistic
            # number of transactions, matching the legacy spreadsheet convention)
            ws.cell(row=2, column=desc_col).value = "Total"
            ws.cell(row=2, column=desc_col).font = bold
            ws.cell(row=2, column=amt_col).value = (
                f"=SUM({amt_col_letter}6:{amt_col_letter}1001)"
            )

            # Row 3: "Budget" | budgeted amount (0 when not supplied by input)
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
            cat_df = transactions_per_category[cat]
            for row_offset, tx_row in cat_df.iterrows():
                excel_row = 6 + row_offset
                ws.cell(row=excel_row, column=desc_col).value = tx_row["description"]
                ws.cell(row=excel_row, column=amt_col).value = tx_row["amount"]

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
