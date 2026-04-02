import pandas as pd
import re

def parse_receipts(file_path):
    # Load the Excel file
    xl = pd.ExcelFile(file_path)
    
    # List to hold all parsed rows
    all_data = []

    # 2) Skip the first sheet
    sheet_names = xl.sheet_names[1:]

    for sheet_name in sheet_names:
        # 3) Parse date from sheet name: "3 2026 Receipts" -> 3/1/2026
        # Logic: Split by space, take first as month, second as year
        parts = sheet_name.split(' ')
        if len(parts) >= 2:
            month = parts[0]
            year = parts[1]
            date_str = f"{month}/1/{year}"
        else:
            continue # Skip sheets that don't match the naming convention

        # Read the sheet (header=None so we can access Row 1 and Row 6 via index)
        df = xl.parse(sheet_name, header=None)

        # Row 1 is index 0. Row 6 starts at index 5.
        # Merged categories appear every 2 columns (0, 2, 4...)
        num_cols = df.shape[1]
        
        for col_idx in range(0, num_cols, 2):
            # Get the category from Row 1 (index 0)
            category_raw = df.iloc[0, col_idx]
            
            # If the cell is empty, skip this column pair
            if pd.isna(category_raw):
                continue
                
            category = str(category_raw).lower().strip()

            # Process transactions starting from Row 6 (index 5)
            # Left cell (col_idx) = description, Right cell (col_idx + 1) = amount
            for row_idx in range(5, len(df)):
                description = df.iloc[row_idx, col_idx]
                amount_raw = df.iloc[row_idx, col_idx + 1]

                # Only add if we have at least a description or an amount
                if pd.notna(description) or pd.notna(amount_raw):
                    
                    # Clean the amount: convert to float, remove symbols
                    amount = 0.0
                    if pd.notna(amount_raw):
                        # Remove $, commas, and whitespace
                        clean_amount = re.sub(r'[^-?\d.]', '', str(amount_raw))
                        try:
                            amount = float(clean_amount)
                        except ValueError:
                            amount = 0.0

                    all_data.append({
                        "date": date_str,
                        "category": category,
                        "description": str(description) if pd.notna(description) else "",
                        "amount": amount,
                        "isPositive": True if category in ['income'] else False
                    })

    return pd.DataFrame(all_data)

if __name__ == "__main__":
    # Usage
    result_df = parse_receipts('/Users/steven/Downloads/Budget (1).xlsx')
    print(result_df.head())
    result_df.to_csv('cleaned_receipts.csv', index=False)