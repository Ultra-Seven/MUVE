from dateutil.parser import parse
import pandas as pd
import sys
import csv

"""
Expand & Preprocess CSV file to allow for better user queries

@author Connor Anderson

"""

num_to_month = {1: "January", 2: "February",
                3: "March", 4: "April",
                5: "May", 6: "June",
                7: "July", 8: "August",
                9: "September", 10: "October",
                11: "November", 12: "December"}


def is_date(string, fuzzy=False):
    """
    Return whether the string can be interpreted as a date.

    :param string: str, string to check for date
    :param fuzzy: bool, ignore unknown tokens in string if True
    """
    try:
        parse(string, fuzzy=fuzzy)
        return True

    except ValueError:
        return False


def time_of_day(hour, minute, second):
    """
    Return English representation of a given hour in the day.

    :param hour: the hour, given in military time
    """
    if hour == 0 and minute == 0 and second == 0:
        return ""
    if hour < 6 or hour > 20:
        return "Night"
    if hour > 5 and hour < 12:
        return "Morning"
    if hour > 11 and hour < 16:
        return "Afternoon"
    if hour > 15 and hour < 21:
        return "Evening"


def map_columns(file):
    """
    Return mapping from column name to (column number, isDate) tuple.

    :param file: a CSV file with column headers
    """
    mapping = {}
    idx = 0
    for column in file:
        # Pull sample value for column
        sample_val = file.iloc[0, idx]
        # Determine whether a column is of type date and generate mapping
        if isinstance(sample_val, str):
            mapping[column] = (idx, int(is_date(file.iloc[0, idx])))
        else:
            mapping[column] = (idx, 0)
        idx += 1
    return mapping


def initialize_chunk(chunk, f):
    """
    Return mapping from column name to a tuple containing the
    column index and a boolean representing whether the column
    is a date, while initializing headers of CSV file.

    :param chunk: the first block of the CSV file
    :param file: output file
    """
    col_mapping = map_columns(chunk)
    headers = []
    for col in chunk:
        if col_mapping[col][1] == True:
            headers.append(col + " Year")
            headers.append(col + " Month")
            headers.append(col + " Day")
            headers.append(col + " Time Of Day")
        else:
            headers.append(col)
    f.writerow(headers)
    return col_mapping


def process_chunk(chunk, mapping, f):
    """
    Return void, writes updated version of chunk to output file.

    :param chunk: the first block of the CSV file
    :param file: output file
    :param mapping: mapping from column name to a tuple containing the
      column index and a boolean representing whether the column
    is a date
    """
    for idx in range(len(chunk)):
        row = []
        for col in chunk:
            if mapping[col][1] == True:
                try:
                    date = parse(chunk.iloc[idx, mapping[col][0]], fuzzy=True)
                    row.append(str(date.year))
                    row.append(num_to_month[date.month])
                    row.append(str(date.day))
                    row.append(time_of_day(
                        date.hour, date.minute, date.second))
                except TypeError:
                    row.append("")
                    row.append("")
                    row.append("")
                    row.append("")
            else:
                row.append(str(chunk.iloc[idx, mapping[col][0]]))
        f.writerow(row)


def preprocess(path):
    """
    Return void, converts CSV file at given path to expand on all date columns
    in the file to allow for easier voice querying.

    :param path: path to the CSV the user wishes to change
    """
    col_mapping = {}
    f = csv.writer(open('processed.csv', 'w+'))
    initialized = False
    try:
        for chunk in pd.read_csv(path, chunksize=10000):
            if not initialized:
                col_mapping = initialize_chunk(chunk, f)
                initialized = True
                process_chunk(chunk, col_mapping, f)
            else:
                process_chunk(chunk, col_mapping, f)
    except OSError:
        print("Please provide a valid file path")


if __name__ == "__main__":

    if len(sys.argv) != 2:
        print(
            "Please provide a working path to your CSV file as the first and only argument")
        print("Example: \"../../../../Desktop/311files/sample311.csv\"")
        sys.exit()

    path = sys.argv[1]
    preprocess(path)
