package ca.ualberta.ssrg.ga;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitManager {
	public static String getRemoteUrl(String repoPath) throws IOException {
		return Git.open(new File(repoPath)).getRepository().getConfig().getString("remote", "origin", "url");
	}
	public static String getHeadCommitKey(String repoPath) throws NoHeadException, GitAPIException, IOException {
		return Git.open(new File(repoPath)).log().call().iterator().next().getName();
	}
	public static String[] diffVersions(String repoPath, String vers1, String vers2)
	{
		File gitDir = new File(repoPath+"/.git");
		Repository repository ;
		String outText = "";
		try {
			repository = new FileRepository(gitDir);
			try {
				Git git = Git.open(new File(repoPath));
				Iterator<RevCommit> iterator = git.log().call().iterator();
				RevCommit ver1 = null,ver2 = null;
				git.close();

				RevCommit rc2;
				boolean v1 = false, v2 = false, v3=false;
				while((rc2=iterator.next())!=null)
				{
					if(rc2.getName().equals(vers1 ))
					{
						ver1 = rc2;
						v1 = true;
						if(v2)
							v3=true;
					}
					else if(rc2.getName().equals(vers2 ))
					{ 
						ver2 =rc2;
						v2 = true;
					}

					if(v1 && v2)
						break;

				}

				if(!v1)
				{
					throw new Exception("Version: " + vers1 + " not present in Git, please check the Git path");
				}

				if(!v2)
				{
					throw new Exception("Version: " + vers2 + " not present in Git, please check the Git path");
				}

				if(v3)
				{
					rc2=ver1;
					ver1=ver2;
					ver2=rc2;
				}

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				DiffFormatter df = new DiffFormatter(out);
				df.setRepository(repository);

				List<DiffEntry> diffs = df.scan(ver2.getTree(), ver1.getTree());

				for (DiffEntry diff : diffs) {

					df.format(diff);
					String diffText = out.toString("UTF-8");
					out.reset();
					if(diff.getNewPath().toString().endsWith(".java"))
						outText+="\n<b>"+diffText.split("\\n", 2)[0]+"</b>\n"+diffText.split("\\n", 2)[1];
				}
			}catch(Exception e)
			{
				e.printStackTrace();
			}

		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return outText.split("diff --git");
	}
}