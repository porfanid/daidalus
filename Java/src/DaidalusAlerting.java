/**

 Notices:

 Copyright 2016 United States Government as represented by the
 Administrator of the National Aeronautics and Space Administration. No
 copyright is claimed in the United States under Title 17,
 U.S. Code. All Other Rights Reserved.

 Disclaimers

 No Warranty: THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY
 WARRANTY OF ANY KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY,
 INCLUDING, BUT NOT LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE
 WILL CONFORM TO SPECIFICATIONS, ANY IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR FREEDOM FROM
 INFRINGEMENT, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL BE ERROR
 FREE, OR ANY WARRANTY THAT DOCUMENTATION, IF PROVIDED, WILL CONFORM TO
 THE SUBJECT SOFTWARE. THIS AGREEMENT DOES NOT, IN ANY MANNER,
 CONSTITUTE AN ENDORSEMENT BY GOVERNMENT AGENCY OR ANY PRIOR RECIPIENT
 OF ANY RESULTS, RESULTING DESIGNS, HARDWARE, SOFTWARE PRODUCTS OR ANY
 OTHER APPLICATIONS RESULTING FROM USE OF THE SUBJECT SOFTWARE.
 FURTHER, GOVERNMENT AGENCY DISCLAIMS ALL WARRANTIES AND LIABILITIES
 REGARDING THIRD-PARTY SOFTWARE, IF PRESENT IN THE ORIGINAL SOFTWARE,
 AND DISTRIBUTES IT "AS IS."

 Waiver and Indemnity: RECIPIENT AGREES TO WAIVE ANY AND ALL CLAIMS
 AGAINST THE UNITED STATES GOVERNMENT, ITS CONTRACTORS AND
 SUBCONTRACTORS, AS WELL AS ANY PRIOR RECIPIENT.  IF RECIPIENT'S USE OF
 THE SUBJECT SOFTWARE RESULTS IN ANY LIABILITIES, DEMANDS, DAMAGES,
 EXPENSES OR LOSSES ARISING FROM SUCH USE, INCLUDING ANY DAMAGES FROM
 PRODUCTS BASED ON, OR RESULTING FROM, RECIPIENT'S USE OF THE SUBJECT
 SOFTWARE, RECIPIENT SHALL INDEMNIFY AND HOLD HARMLESS THE UNITED
 STATES GOVERNMENT, ITS CONTRACTORS AND SUBCONTRACTORS, AS WELL AS ANY
 PRIOR RECIPIENT, TO THE EXTENT PERMITTED BY LAW.  RECIPIENT'S SOLE
 REMEDY FOR ANY SUCH MATTER SHALL BE THE IMMEDIATE, UNILATERAL
 TERMINATION OF THIS AGREEMENT.
 **/

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gov.nasa.larcfm.ACCoRD.Alerter;
import gov.nasa.larcfm.ACCoRD.ConflictData;
import gov.nasa.larcfm.ACCoRD.Daidalus;
import gov.nasa.larcfm.ACCoRD.DaidalusFileWalker;
import gov.nasa.larcfm.ACCoRD.DaidalusParameters;
import gov.nasa.larcfm.ACCoRD.Detection3D;
import gov.nasa.larcfm.ACCoRD.WCV_tvar;
import gov.nasa.larcfm.Util.ParameterData;
import gov.nasa.larcfm.Util.Units;
import gov.nasa.larcfm.Util.f;

public class DaidalusAlerting {

	public static void main(String[] args) {
		Daidalus daa = new Daidalus();

		String inputFile = null;
		String outputFile = null;
		String ownship = null;
		List<String> traffic = new ArrayList<>();
		ParameterData params = new ParameterData();
		String conf = "";
		boolean echo = false;
		int precision = 6;

		for (int i = 0; i < args.length; ++i) {
			String arg = args[i];
			switch (arg) {
				case "--echo":
				case "-echo":
					echo = true;
					break;
				case "--prec":
				case "-prec":
					precision = Integer.parseInt(args[++i]);
					break;
				case "--o":
				case "-o":
					outputFile = args[++i];
					break;
				case "--own":
				case "-own":
					ownship = args[++i];
					break;
				case "--traf":
				case "-traf":
					traffic.addAll(Arrays.asList(args[++i].split(",")));
					break;
				case "--help":
				case "-h":
					printUsage();
					System.exit(0);
					break;
				default:
					if (arg.startsWith("--c") || arg.startsWith("-c")) {
						conf = args[++i];
						configureDAA(daa, conf);

					} else if (arg.startsWith("-") && arg.contains("=")) {
						params.set(arg.substring(arg.lastIndexOf('-') + 1));
					} else if (inputFile == null) {
						inputFile = arg;
					} else {
						System.err.println("** Error: Only one input file can be provided");
						System.exit(1);
					}
					break;
			}
		}

		if (daa.numberOfAlerters() == 0) {
			daa.set_DO_365B();
		}

		if (!params.isEmpty()) {
			daa.setParameterData(params);
		}

		if (inputFile == null) {
			if (echo) {
				System.out.print(daa.toString());
			} else {
				System.err.println("** Error: One input file must be provided");
			}
			System.exit(1);
		}

		File file = new File(inputFile);
		if (!file.exists() || !file.canRead()) {
			System.err.println("** Error: File " + inputFile + " cannot be read");
			System.exit(1);
		}

		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile != null ? outputFile : generateOutputFileName(file.getName(), conf))), true)) {
			processFile(daa, file, out, precision, echo, ownship, traffic);
		} catch (Exception e) {
			System.err.println("** Error: " + e);
			System.exit(1);
		}
	}

	private static void configureDAA(Daidalus daa, String configArg) {
		if (!daa.loadFromFile(configArg)) {
			switch (configArg) {
				case "sum":
					daa.set_DO_365B(true, true);
					break;
				case "no_sum":
					daa.set_DO_365B(true, false);
					break;
				case "nom_a":
					daa.set_Buffered_WC_DO_365(false);
					break;
				case "nom_b":
					daa.set_Buffered_WC_DO_365(true);
					break;
				case "cd3d":
					daa.set_CD3D();
                    break;
				case "tcasii":
					daa.set_TCASII();
					break;
				default:
					System.err.println("** Error: File " + configArg + " not found");
					System.exit(1);
			}
		} else {
			System.err.println("Loading configuration file " + configArg);
		}
	}

	private static void processFile(Daidalus daa, File file, PrintWriter out, int precision, boolean echo, String ownship, List<String> traffic) {
		DaidalusFileWalker walker = new DaidalusFileWalker(file.getPath());

		if (ownship != null) {
			walker.setOwnship(ownship);
		}
		if (!traffic.isEmpty()) {
			walker.selectTraffic(traffic);
		}

		DaidalusParameters.setDefaultOutputPrecision(precision);
		System.err.println("Processing DAIDALUS file " + file.getName());
		System.err.println("Generating CSV file " + file.getName().replaceFirst("[.][^.]+$", "") + ".csv");
		printHeader(daa, out);
		while (!walker.atEnd()) {
			walker.readState(daa);
			if (echo) {
				System.out.print(daa.toString());
			}
			writeData(daa, out);
		}
	}

	private static void printHeader(Daidalus daa, PrintWriter out) {
		int maxAlertLevels = daa.maxNumberOfAlertLevels();
		Detection3D detector = daa.getAlerterAt(1).getDetector(daa.correctiveAlertLevel(1)).orElse(null);

		out.print("Time, Ownship, Traffic, Alerter, Alert Level");

		if (!daa.isDisabledDTALogic()) {
			out.print(", DTA Active, DTA Guidance, Distance to DTA");
		}

		for (int level = 1; level <= maxAlertLevels; ++level) {
			out.print(", Time to Volume of Alert(" + level + ")");
		}

		out.print(", Horizontal Separation, Vertical Separation, Horizontal Closure Rate, Vertical Closure Rate, Projected HMD, Projected VMD, Projected TCPA, Projected DCPA, Projected TCOA");

		if (detector instanceof WCV_tvar) {
			out.print(", Projected TAUMOD (WCV*)");
		}
		out.println();

		String uhor = daa.getUnitsOf("min_horizontal_recovery");
		String uver = daa.getUnitsOf("min_vertical_recovery");
		String uhs = daa.getUnitsOf("step_hs");
		String uvs = daa.getUnitsOf("step_vs");

		out.print("[s],,,,");
		if (!daa.isDisabledDTALogic()) {
			out.print(",,,[nmi]");
		}
		for (int level = 1; level <= maxAlertLevels; ++level) {
			out.print(",[s]");
		}
		out.print(",[" + uhor + "],[" + uver + "],[" + uhs + "],[" + uvs + "],[" + uhor + "],[" + uver + "],[s],[" + uhor + "],[s]");
		if (detector instanceof WCV_tvar) {
			out.print(",[s]");
		}
		out.println();
	}

	private static void writeData(Daidalus daa, PrintWriter out) {

		for (int i = 1; i <= daa.lastTrafficIndex(); ++i) {
			int alerterIndex = daa.alerterIndexBasedOnAlertingLogic(i);
			Alerter alerter = daa.getAlerterAt(alerterIndex);

			if (!alerter.isValid()) {
				continue;
			}

			out.print(f.FmPrecision(daa.getCurrentTime()) + ", " + daa.getOwnshipState().getId() + ", " + daa.getAircraftStateAt(i).getId() + ", " + alerterIndex);
			out.print(", " + daa.alertLevel(i));

			if (!daa.isDisabledDTALogic()) {
				out.print(", " + daa.isActiveDTALogic() + ", " + (daa.isActiveDTASpecialManeuverGuidance() ? (daa.isEnabledDTALogicWithHorizontalDirRecovery() ? "Departing" : "Landing") : ""));
				double dh = (daa.isAlertingLogicOwnshipCentric() ? daa.getOwnshipState() : daa.getAircraftStateAt(i)).getPosition().distanceH(daa.getDTAPosition());
				out.print(", " + f.FmPrecision(Units.to("nmi", dh)));
			}

			for (int level = 1; level <= alerter.mostSevereAlertLevel(); ++level) {
				ConflictData det = daa.violationOfAlertThresholds(i, level);
				out.print(", " + f.FmPrecision(det.getTimeIn()));
			}

			out.print(", " + f.FmPrecision(daa.currentHorizontalSeparation(i, daa.getUnitsOf("min_horizontal_recovery"))) + ", " + f.FmPrecision(daa.currentVerticalSeparation(i, daa.getUnitsOf("min_vertical_recovery"))) + ", " + f.FmPrecision(daa.horizontalClosureRate(i, daa.getUnitsOf("step_hs"))) + ", " + f.FmPrecision(daa.verticalClosureRate(i, daa.getUnitsOf("step_vs"))) + ", " + f.FmPrecision(daa.predictedHorizontalMissDistance(i, daa.getUnitsOf("min_horizontal_recovery"))) + ", " + f.FmPrecision(daa.predictedVerticalMissDistance(i, daa.getUnitsOf("min_vertical_recovery"))) + ", " + f.FmPrecision(daa.timeToHorizontalClosestPointOfApproach(i)) + ", " + f.FmPrecision(daa.distanceAtHorizontalClosestPointOfApproach(i, daa.getUnitsOf("min_horizontal_recovery"))));
			double tcoa = daa.timeToCoAltitude(i);
			out.print(", " + (tcoa >= 0 ? f.FmPrecision(tcoa) : ""));

			if (daa.getAlerterAt(1).getDetector(daa.correctiveAlertLevel(1)).orElse(null) instanceof WCV_tvar) {
				double tauMod = daa.modifiedTau(i, ((WCV_tvar) daa.getAlerterAt(1).getDetector(daa.correctiveAlertLevel(1)).orElse(null)).getDTHR());
				out.print(", " + (tauMod >= 0 ? f.FmPrecision(tauMod) : ""));
			}

			out.println();
		}
	}

	private static String generateOutputFileName(String name, String conf) {
		String scenario = name.substring(0, name.lastIndexOf('.'));
		return (conf.isEmpty() ? scenario : scenario + "_" + conf) + ".csv";
	}

	private static void printUsage() {
		System.err.println("Usage:");
		System.err.println("  DaidalusAlerting [<option>] <daa_file>");
		System.err.println("  <option> can be");
		System.err.println("  --config <configuration-file> | no_sum | nom_a | nom_b | cd3d | tcasii\n\tLoad <configuration-file>");
		System.err.println("  --<var>=<val>\n\t<key> is any configuration variable and val is its value (including units, if any), e.g., --lookahead_time=5[min]");
		System.err.println("  --output <output_file>\n\tOutput information to <output_file>");
		System.err.println("  --echo\n\tEcho configuration and traffic list in standard output");
		System.err.println("  --precision <n>\n\tOutput decimal precision");
		System.err.println("  --ownship <id>\n\tSpecify a particular aircraft as ownship");
		System.err.println("  --traffic <id1>,..,<idn>\nSpecify a list of aircraft as traffic");
		System.err.println("  --help\n\tPrint this message");
	}
}