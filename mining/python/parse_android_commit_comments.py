import bop, json, re

def save_json(f_name, results):
    with open(f_name, 'w') as outfile:
            json.dump(results, outfile)

def load_json(f_name):
    return json.load(open(f_name, 'r'))

def filter(data, pattern):
    result = {}
    for repo in data:
        new_repo = {}
        for rev in data[repo]:
            if re.search(pattern, data[repo][rev]["log"]):
                new_repo[rev] = data[repo][rev]
        if len(new_repo) != 0:
            result[repo] = new_repo
    return result

with open("/Users/meysam/Desktop/output.txt") as in_file:
    parsed_boa = bop.parse(in_file)
    data = {}
    for repo in parsed_boa:
        repo_data = {}
        revs = parsed_boa[repo].split("$$$REV$$$:")
        for rev in revs:
            rev_data = {}
            if rev:
                date_splits = rev.split(" $$$DATE$$$:")
                rev_id = date_splits[0]
                log_splits = date_splits[1].split(" $$$LOG$$$:")
                rev_date = log_splits[0]
                rev_log = log_splits[1]
                rev_data["date"] = rev_date
                rev_data["log"] = rev_log
                repo_data[rev_id] = rev_data
        data[repo] = repo_data
    save_json("out.json", data)

def get_size(data):
    sum = 0
    for repo in data:
        sum += len(data[repo])
    return sum
