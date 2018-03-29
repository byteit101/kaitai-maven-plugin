package name.valery1707.kaitai;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static java.lang.String.format;
import static name.valery1707.kaitai.MojoUtils.*;

/**
 * @see <a href="http://maven.apache.org/developers/mojo-api-specification.html">Mojo API Specification</a>
 */
@Mojo(
	name = "generate"
	, defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class KaitaiMojo extends AbstractMojo {
	private static final String URL_FORMAT = "https://dl.bintray.com/kaitai-io/universal/%s/kaitai-struct-compiler-%s.zip";
	private static final String KAITAI_START_SCRIPT = "kaitai-struct-compiler.bat";

	/**
	 * Version of <a href="http://kaitai.io/#download">KaiTai</a> library.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.version", defaultValue = "0.8")
	private String version;

	/**
	 * Direct link onto <a href="http://kaitai.io/#download">KaiTai universal zip archive</a>.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.url")
	private URL url;

	/**
	 * Cache directory for download KaiTai library.
	 *
	 * @see KaitaiMojo#version
	 * @see KaitaiMojo#url
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.cache")
	private File cacheDir;

	/**
	 * Source directory with <a href="http://formats.kaitai.io/">Kaitai Struct language</a> files.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.source", defaultValue = "${project.build.sourceDirectory}/resources/kaitai")
	private File sourceDirectory;

	/**
	 * Include wildcard pattern list.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.includes", defaultValue = "*.ksy")
	private String[] includes;

	/**
	 * Exclude wildcard pattern list.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.excludes")
	private String[] excludes;

	/**
	 * Target directory for generated Java source files.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.output", defaultValue = "${project.build.directory}/generated-sources/kaitai")
	private File output;

	/**
	 * Skip plugin execution (don't read/validate any files, don't generate any java types).
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.skip", defaultValue = "false")
	private boolean skip = false;

	/**
	 * Overwrite exists files in target directory.
	 *
	 * @since 0.1.0
	 */
	@Parameter(property = "kaitai.overwrite", defaultValue = "false")
	private boolean overwrite = false;

	@Parameter(defaultValue = "${settings}", readonly = true)
	private Settings settings;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession session;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	/**
	 * Executes the plugin, to read the given source and behavioural properties and generate POJOs.
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			getLog().info("Skip KaiTai generation: skip=true");
			return;
		}

		//Scan source files
		List<Path> source = scanFiles(sourceDirectory.toPath(), includes, excludes);
		if (source.isEmpty()) {
			getLog().warn(
				"Skip KaiTai generation: Not found any input files in "
					+ sourceDirectory.toPath().normalize().toFile().getAbsolutePath()
			);
			return;
		}

		URL url = prepareUrl(this.url, version);

		Path cacheDir = prepareCache(this.cacheDir, session);

		//Download Kaitai distribution into cache and unzip it
		Path kaitaiBat = downloadKaitai(url, cacheDir);

		Path output = mkdirs(this.output.toPath());
		//todo Generate Java sources
		//todo Add generated directory into Maven's build scope
		project.addCompileSourceRoot(output.normalize().toFile().getAbsolutePath());
	}

	static URL prepareUrl(URL url, String version) throws MojoExecutionException {
		if (url == null) {
			try {
				url = new URL(format(URL_FORMAT, version, version));
			} catch (MalformedURLException e) {
				throw new MojoExecutionException("Invalid version: " + version, e);
			}
		}
		return url;
	}

	static Path prepareCache(File target, MavenSession session) throws MojoExecutionException {
		Path cache;
		if (target == null) {
			Path repository = new File(session.getLocalRepository().getBasedir()).toPath();
			cache = repository.resolve(".cache").resolve("kaitai").normalize();
		} else {
			cache = target.toPath();
		}
		return mkdirs(cache);
	}

	static Path downloadKaitai(URL url, Path cacheDir) throws MojoExecutionException {
		Path distZip = cacheDir.resolve(url.getFile());
		download(url, distZip);
		Path dist = unpack(distZip);
		List<Path> bats = scanFiles(dist, new String[]{KAITAI_START_SCRIPT}, new String[0]);
		if (bats.size() != 1) {
			throw new MojoExecutionException(format(
				"Fail to find start script '%s' in Kaitai distribution: %s"
				, KAITAI_START_SCRIPT
				, dist.normalize().toFile().getAbsolutePath()
			));
		}
		return bats.get(0);
	}
}
