import argparse
import os

import pandas as pd


def export_to_excel(input_file_path, output_file_path="budget_export.xlsx"):
    """
    Convert a CSV or JSON file (as produced by parse_receipts.py) back into
    the legacy Excel spreadsheet format.

    Input columns expected: date, category, description, amount

    The date column may use any format pandas can parse, including
    "M/D/YYYY" (e.g. "3/1/2026"), "YYYY-MM-DD" (e.g. "2026-03-01"),
    or datetime/Timestamp objects that pandas produces when reading JSON.

    Output Excel structure:
    - Sheet 1: "Summary" (placeholder; parse_receipts.py skips the first sheet)
    - One additional sheet per month, named "M YYYY Receipts" (e.g. "3 2026 Receipts")
      - Row 1  (index 0): category names in columns 0, 2, 4, ...
      - Rows 2-5 (indices 1-4): empty
      - Row 6+  (index 5+): description (left col) / amount (right col) per category

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

    with pd.ExcelWriter(output_file_path, engine="openpyxl") as writer:
        # First sheet: placeholder (parse_receipts.py skips the first sheet)
        pd.DataFrame().to_excel(writer, sheet_name="Summary", index=False)

        # One sheet per month, ordered chronologically in ascending order
        month_groups = (
            df.groupby(["_year", "_month"], sort=True)
        )

        for (year, month), group in sorted(
            month_groups, key=lambda x: (x[0][0], x[0][1])
        ):
            sheet_name = f"{month} {year} Receipts"

            # Determine the unique categories for this month (preserve insertion order)
            categories = list(dict.fromkeys(group["category"].tolist()))

            # Build the sheet data as a 2-D list of rows
            # Determine the number of rows needed: 5 header rows + max transactions
            transactions_per_category = {
                cat: group[group["category"] == cat].reset_index(drop=True)
                for cat in categories
            }
            max_rows = max(
                (len(t) for t in transactions_per_category.values()), default=0
            )
            total_rows = 5 + max_rows  # rows 0-4 are header area; rows 5+ are data

            num_cols = len(categories) * 2
            sheet_data = [
                [None] * num_cols for _ in range(total_rows)
            ]

            # Row 0 (Excel row 1): category names in columns 0, 2, 4, ...
            for col_pair, cat in enumerate(categories):
                sheet_data[0][col_pair * 2] = cat

            # Rows 5+ (Excel rows 6+): description (left) and amount (right)
            for col_pair, cat in enumerate(categories):
                cat_df = transactions_per_category[cat]
                for row_offset, tx_row in cat_df.iterrows():
                    row_idx = 5 + row_offset
                    sheet_data[row_idx][col_pair * 2] = tx_row["description"]
                    sheet_data[row_idx][col_pair * 2 + 1] = tx_row["amount"]

            pd.DataFrame(sheet_data).to_excel(
                writer, sheet_name=sheet_name, index=False, header=False
            )

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
