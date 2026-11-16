package io.anf.games;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.*;
import static org.junit.Assert.*;
public class PackageStoreTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    File archive(String path,String value)throws Exception{File z=temp.newFile();try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(z))){out.putNextEntry(new ZipEntry(path));out.write(value.getBytes());out.closeEntry();}return z;}
    @Test public void verifiedInstallAndUpdate()throws Exception{PackageStore s=new PackageStore(temp.newFolder());File a=archive("index.html","one");s.install("game",a,PackageStore.sha256(a));File b=archive("index.html","two");s.install("game",b,PackageStore.sha256(b));assertEquals("two",new String(Files.readAllBytes(new File(s.current("game"),"index.html").toPath())));s.remove("game");assertNull(s.current("game"));}
    @Test public void badChecksumPreservesInstalledGame()throws Exception{PackageStore s=new PackageStore(temp.newFolder());File a=archive("index.html","one");String hash=PackageStore.sha256(a);s.install("game",a,hash);File b=archive("index.html","two");assertThrows(IOException.class,()->s.install("game",b,hash));assertEquals(hash,s.current("game").getName());}
    @Test public void rejectsTraversal()throws Exception{File z=archive("../escaped.txt","bad");assertThrows(IOException.class,()->PackageStore.extract(z,temp.newFolder(),1024));assertFalse(new File(temp.getRoot(),"escaped.txt").exists());}
    @Test public void rejectsOversizedExtraction()throws Exception{File z=archive("index.html","123456");assertThrows(IOException.class,()->PackageStore.extract(z,temp.newFolder(),5));}
    @Test public void missingEntrypointPreservesOldInstall()throws Exception{PackageStore s=new PackageStore(temp.newFolder());File a=archive("index.html","one");String h=PackageStore.sha256(a);s.install("game",a,h);File b=archive("other.html","two");assertThrows(IOException.class,()->s.install("game",b,PackageStore.sha256(b)));assertEquals(h,s.current("game").getName());}
    @Test public void rejectsBadGameId(){assertThrows(IllegalArgumentException.class,()->PackageStore.validId("../outside"));}
    @Test public void cancellationLeavesNoInstall()throws Exception{PackageStore s=new PackageStore(temp.newFolder());File a=archive("index.html","one");String h=PackageStore.sha256(a);Thread.currentThread().interrupt();try{assertThrows(InterruptedIOException.class,()->s.install("game",a,h));}finally{Thread.interrupted();}assertNull(s.current("game"));}
}
