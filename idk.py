import pandas as pd

# Load your timing CSV
df = pd.read_csv("timings.csv")

# Ensure numeric type
df["millis"] = pd.to_numeric(df["millis"], errors="coerce")

# Get unique phase types
phases = df["phase"].unique()

for phase in phases:
    subset = df[df["phase"] == phase]["millis"].dropna().nsmallest(50)
    print(f"\n--- {phase}: {len(subset)} smallest values ---")
    print(subset.to_list())
