"""Round-trip tests for parse_receipts.py and export_to_excel.py.

Verifies that data written by export_to_excel can be read back by
parse_receipts and produces an identical dataset.
"""

import os
import tempfile

import pandas as pd
import pytest

from export_to_excel import export_to_excel
from parse_receipts import parse_receipts


SAMPLE_ROWS = [
    {
        "date": "3/1/2026",
        "category": "groceries",
        "description": "Whole Foods",
        "amount": 45.50,
        "isPositive": False,
    },
    {
        "date": "3/1/2026",
        "category": "groceries",
        "description": "Trader Joe's",
        "amount": 78.25,
        "isPositive": False,
    },
    {
        "date": "3/1/2026",
        "category": "utilities",
        "description": "Electric Bill",
        "amount": 120.00,
        "isPositive": False,
    },
    {
        "date": "3/1/2026",
        "category": "income",
        "description": "Salary",
        "amount": 3000.00,
        "isPositive": True,
    },
    {
        "date": "4/1/2026",
        "category": "groceries",
        "description": "Costco",
        "amount": 200.00,
        "isPositive": False,
    },
    {
        "date": "4/1/2026",
        "category": "utilities",
        "description": "Water Bill",
        "amount": 60.00,
        "isPositive": False,
    },
]


def _make_input_df():
    return pd.DataFrame(SAMPLE_ROWS)


# ---------------------------------------------------------------------------
# Round-trip via CSV
# ---------------------------------------------------------------------------


def test_round_trip_csv():
    """CSV -> export_to_excel -> parse_receipts reproduces the original data."""
    original = _make_input_df()

    with tempfile.TemporaryDirectory() as tmp:
        csv_path = os.path.join(tmp, "input.csv")
        xlsx_path = os.path.join(tmp, "output.xlsx")

        original.to_csv(csv_path, index=False)
        export_to_excel(csv_path, xlsx_path)
        result = parse_receipts(xlsx_path)

    result = result.reset_index(drop=True)
    original = original.reset_index(drop=True)

    assert list(result["date"]) == list(original["date"])
    assert list(result["category"]) == list(original["category"])
    assert list(result["description"]) == list(original["description"])
    assert list(result["amount"]) == pytest.approx(list(original["amount"]))
    assert list(result["isPositive"]) == list(original["isPositive"])


# ---------------------------------------------------------------------------
# Round-trip via JSON
# ---------------------------------------------------------------------------


def test_round_trip_json():
    """JSON -> export_to_excel -> parse_receipts reproduces the original data."""
    original = _make_input_df()

    with tempfile.TemporaryDirectory() as tmp:
        json_path = os.path.join(tmp, "input.json")
        xlsx_path = os.path.join(tmp, "output.xlsx")

        original.to_json(json_path, orient="records")
        export_to_excel(json_path, xlsx_path)
        result = parse_receipts(xlsx_path)

    result = result.reset_index(drop=True)
    original = original.reset_index(drop=True)

    assert list(result["date"]) == list(original["date"])
    assert list(result["category"]) == list(original["category"])
    assert list(result["description"]) == list(original["description"])
    assert list(result["amount"]) == pytest.approx(list(original["amount"]))
    assert list(result["isPositive"]) == list(original["isPositive"])


# ---------------------------------------------------------------------------
# Excel structure
# ---------------------------------------------------------------------------


def test_excel_sheet_names():
    """Exported workbook has a Summary sheet plus one sheet per month."""
    original = _make_input_df()

    with tempfile.TemporaryDirectory() as tmp:
        csv_path = os.path.join(tmp, "input.csv")
        xlsx_path = os.path.join(tmp, "output.xlsx")

        original.to_csv(csv_path, index=False)
        export_to_excel(csv_path, xlsx_path)

        xl = pd.ExcelFile(xlsx_path)
        assert xl.sheet_names[0] == "Summary"
        assert "3 2026 Receipts" in xl.sheet_names
        assert "4 2026 Receipts" in xl.sheet_names


def test_excel_category_row():
    """Row 1 of each data sheet contains category names at every other column."""
    original = _make_input_df()

    with tempfile.TemporaryDirectory() as tmp:
        csv_path = os.path.join(tmp, "input.csv")
        xlsx_path = os.path.join(tmp, "output.xlsx")

        original.to_csv(csv_path, index=False)
        export_to_excel(csv_path, xlsx_path)

        xl = pd.ExcelFile(xlsx_path)
        sheet = xl.parse("3 2026 Receipts", header=None)

    category_row = sheet.iloc[0]
    # Even columns hold category names; odd columns are empty
    even_values = [v for i, v in enumerate(category_row) if i % 2 == 0]
    odd_values = [v for i, v in enumerate(category_row) if i % 2 == 1]

    assert all(pd.notna(v) for v in even_values)
    assert all(pd.isna(v) for v in odd_values)


def test_invalid_file_type_raises():
    """export_to_excel raises ValueError for unsupported file extensions."""
    with tempfile.TemporaryDirectory() as tmp:
        with pytest.raises(ValueError, match="Unsupported file type"):
            export_to_excel("data.txt", os.path.join(tmp, "out.xlsx"))


def test_missing_columns_raises():
    """export_to_excel raises ValueError when required columns are absent."""
    with tempfile.TemporaryDirectory() as tmp:
        csv_path = os.path.join(tmp, "bad.csv")
        pd.DataFrame({"date": ["3/1/2026"], "amount": [10.0]}).to_csv(
            csv_path, index=False
        )
        with pytest.raises(ValueError, match="missing required columns"):
            export_to_excel(csv_path, os.path.join(tmp, "out.xlsx"))
