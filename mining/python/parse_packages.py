import bop
import requests
data = bop.parse(open("/Users/meysam/Desktop/get_all_pakhage_names.out"))
packages = {}
for j in range(0, len(data)):
    repo = data.keys()[j]
    print str(j) + "/" + str(len(data))
    shortests = []
    repo_packages = data[repo].split(';')
    repo_packages.sort()
    cut_index = 0
    for i in range(0, len(repo_packages)):
        if repo_packages[i] != '':
            cut_index = i
            break
    repo_packages = repo_packages[i:]
    shortest = "@";
    for i in range(0, len(repo_packages)):
        package = repo_packages[i]
        if not package.startswith(shortest):
            shortest = package
            shortests.append(shortest)
    packages[repo] = shortests

def is_published(package_name):
    req = requests.get("https://play.google.com/store/apps/details?id=" + package_name)
    if req.text.find("class=\"info-container\"") != -1:
        print "https://play.google.com/store/apps/details?id=" + package_name + ": YES"
        return 1
    elif req.text.find("id=\"error-section\"") != -1:
        print "https://play.google.com/store/apps/details?id=" + package_name + ": NO"
        return 0
    else:
        print "https://play.google.com/store/apps/details?id=" + package_name + ": EXCEPTION"
        return -1

published_repos = {}
for i in range(0, len(packages)):
    repo = packages.keys()[i]
    stop = False
    if stop:
        break
    for package in packages[repo]:
        query = is_published(package)
        if query == 1:
            published_repos[repo] = package
            break
        elif query == -1:
            print "BROKEN AT " + i
            stop = True
            break
    print str(i) + "/" + str(len(packages))
