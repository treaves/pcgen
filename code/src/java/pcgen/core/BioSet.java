/*
 * BioSet.java
 * Copyright 2002 (C) Bryan McRoberts <merton_monk@yahoo.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Created on September 27, 2002, 5:30 PM
 *
 * Current Ver: $Revision$
 * Last Editor: $Author$
 * Last Edited: $Date$
 *
 */
package pcgen.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import pcgen.base.util.CaseInsensitiveMap;
import pcgen.base.util.DoubleKeyMap;
import pcgen.base.util.TripleKeyMapToList;
import pcgen.cdom.base.Constants;
import pcgen.cdom.base.NonInteractive;
import pcgen.cdom.enumeration.PCAttribute;
import pcgen.cdom.enumeration.Region;
import pcgen.util.Logging;

/**
 * {@code BioSet}.
 *
 * @author Bryan McRoberts
 */
public final class BioSet extends PObject implements NonInteractive
{
	private DoubleKeyMap<Region, Integer, AgeSet> ageMap = new DoubleKeyMap<>();

	private CaseInsensitiveMap<Integer> ageNames = new CaseInsensitiveMap<>();

	private TripleKeyMapToList<Region, String, String, String> userMap = new TripleKeyMapToList<>();

	public AgeSet getAgeSet(Region region, int index)
	{
		AgeSet ageSet = ageMap.get(region, index);

		if ((ageSet == null) || !ageSet.hasBonuses())
		{
			ageSet = ageMap.get(Region.getConstant("None"), index);
		}

		return ageSet;
	}

	/**
	 * Get the Age Set named
	 * @param ageCategory
	 * @return age set named
	 */
	public int getAgeSetNamed(final String ageCategory)
	{
		Integer cat = ageNames.get(ageCategory);
		return (cat == null) ? -1 : cat;
	}

	/**
	 * Builds a string describing the bio settings for the specified race
	 * This string is formatted so that it can be read in by BioSetLoader
	 *
	 * @param region The region of the race to be output
	 * @param race   The name of the race to be output
	 * @return String A lst string describing the region's biosets.
	 */
	public String getRacePCCText(final String region, final String race)
	{
		final StringBuilder sb = new StringBuilder(1000);
		sb.append("REGION:").append(region).append("\n\n");

		Region r = Region.getConstant(region);
		final SortedMap<Integer, SortedMap<String, SortedMap<String, String>>> ageSets = getRaceTagsByAge(r, race);

		return appendAgesetInfo(r, ageSets, sb);
	}

	/**
	 * Get the tag for the race
	 * @param region
	 * @param race
	 * @param tag
	 * @return List
	 */
	public List<String> getTagForRace(final String region, final String race, final String tag)
	{
		return getValueInMaps(region, race, tag);
	}

	/**
	 * Add the supplied line to the user map. The user map contains an array with
	 * an entry for each age set. The supplied index is used to ensure that the
	 * value is placed in the correct age bracket.
	 *
	 * @param regionString The region the race is defined in.
	 * @param race The race to be updated.
	 * @param tag The tag to be entered. Must be in the form key:value
	 * @param ageSetIndex The age set to be updated.
	 */
	public void addToUserMap(String regionString, final String race, final String tag, final int ageSetIndex)
	{
		final int x = tag.indexOf(':');

		if (x < 0)
		{
			Logging.errorPrint("Invalid value sent to map: " + tag + " (for Race " + race + ")");

			return; // invalid tag
		}

		Region region = Region.getConstant(regionString);
		String key = tag.substring(0, x);
		List<String> r = userMap.getListFor(region, race, key);
		for (int i = (r == null) ? 0 : r.size(); i < ageSetIndex; i++)
		{
			userMap.addToListFor(region, race, key, "0");
		}
		userMap.addToListFor(region, race, key, tag.substring(x + 1));
	}

	/**
	 * Clear the user map
	 */
	public void clearUserMap()
	{
		userMap.clear();
	}

	/**
	 * Copies the bio data for one race to a new race.
	 *
	 * @param origRegion The region of the original race
	 * @param origRace   The name of the original race
	 * @param copyRegion The region of the target race
	 * @param copyRace   The name of the target race
	 */
	public void copyRaceTags(final String origRegion, final String origRace, final String copyRegion, final String copyRace)
	{
		Region oldr = Region.getConstant(origRegion);
		Region newr = Region.getConstant(copyRegion);
		for (String key : userMap.getTertiaryKeySet(oldr, origRace))
		{
			userMap.addAllToListFor(newr, copyRace, key, userMap.getListFor(oldr, origRace, key));
		}
		final int idx = origRace.indexOf('(');
		String otherRace;
		if (idx >= 0)
		{
			otherRace = origRace.substring(0, idx).trim() + '%';
		}
		else
		{
			otherRace = origRace + '%';
		}
		for (String key : userMap.getTertiaryKeySet(oldr, otherRace))
		{
			userMap.addAllToListFor(newr, copyRace, key, userMap.getListFor(oldr, otherRace, key));
		}
	}

	/**
	 * Randomizes the values of the passed in attributes.
	 *
	 * @param randomizeStr .-delimited list of attributes to randomize. (AGE.HT.WT.EYES.HAIR.SKIN are the possible values.)
	 * @param pc The Player Character
	 */
	public void randomize(final String randomizeStr, final PlayerCharacter pc)
	{
		if ((pc == null) || (pc.getRace() == null))
		{
			return;
		}

		final List<String> ranList = new ArrayList<>();
		final StringTokenizer lineTok = new StringTokenizer(randomizeStr, ".", false);

		while (lineTok.hasMoreTokens())
		{
			final String aString = lineTok.nextToken();

			if (aString.startsWith("AGECAT"))
			{
				generateAge(Integer.parseInt(aString.substring(6)), false, pc);
			}
			else
			{
				ranList.add(aString);
			}
		}

		if (ranList.contains("AGE"))
		{
			generateAge(0, true, pc);
		}

		if (ranList.contains("HT") || ranList.contains("WT"))
		{
			generateHeightWeight(pc);
		}

		if (ranList.contains("EYES"))
		{
			pc.setEyeColor(generateBioValue("EYES", pc));
		}

		if (ranList.contains("HAIR"))
		{
			pc.setPCAttribute(PCAttribute.HAIRCOLOR, generateBioValue("HAIR", pc));
		}

		if (ranList.contains("SKIN"))
		{
			pc.setPCAttribute(PCAttribute.SKINCOLOR, generateBioValue("SKINTONE", pc));
		}
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder(100);
		sb.append("AgeMap: ").append(ageMap.toString()).append("\n");
		sb.append("UserMap: ").append(userMap.toString()).append("\n");

		return sb.toString();
	}

	private static String replaceString(final String argInput, final String replacement, final int value)
	{
		String input = argInput;
		final int x = input.indexOf(replacement);

		if (x >= 0)
		{
			final String output = input.substring(0, x);
			final String appendage = input.substring(x + replacement.length());
			input = output + value + appendage;
		}

		return input;
	}

	/**
	 * Retrieves a collection of the tags defined for a race grouped by
	 * the age brackets.
	 *
	 * @param region The region of the race
	 * @param race   The name of the race.
	 * @return SortedMap A map of the gae brackets. Within each age bracket is a
	 * sorted map of the races (one only) and wihtin this is the tags for that
	 * race and age.
	 */
	private SortedMap<Integer, SortedMap<String, SortedMap<String, String>>> getRaceTagsByAge(Region region, String race)
	{
		// setup a mapped structure
		final SortedMap<Integer, SortedMap<String, SortedMap<String, String>>> ageSets = new TreeMap<>();
		// Read in the user settings, split where necessary and add to the appropriate age bracket
		for (String key : userMap.getTertiaryKeySet(region, race))
		{
			addTagToAgeSet(ageSets, race, key, userMap.getListFor(region, race, key));
		}

		return ageSets;
	}

	private String getTokenNumberInMaps(final String addKey, final int tokenNum, String regionName, String raceName)
	{
		final List<String> r = getValueInMaps(regionName, raceName, addKey);

		if (r == null)
		{
			return null;
		}

		if (r.size() <= tokenNum)
		{
			return "0";
		}

		return r.get(tokenNum);
	}

	public List<String> getValueInMaps(final String argRegionName, final String argRaceName, final String addKey)
	{
		final String anotherRaceName;

		if (argRaceName.indexOf('(') >= 0)
		{
			anotherRaceName = argRaceName.substring(0, argRaceName.indexOf('(')).trim() + '%';
		}
		else
		{
			anotherRaceName = argRaceName + '%';
		}
		return mapFind(userMap, argRegionName, argRaceName, addKey, anotherRaceName);
	}

	/**
	 * Adds the tag (key & value) to the supplied ageSets collection. It is
	 * assumed that the ageSet already has an entry for each age bracket and
	 * that this entry will be a SortedMap of races. Each race will contain a
	 * SortedMap of tags and their values.<br> The key is assumed to be of the
	 * form region.race.tag eg "Custom.Human%.MAXAGE" The value is assumed to be
	 * either a list of values or a single value, depending on the tag. eg
	 * "[34,52,69,110]" or "Blond|Brown" If a single value, it will be added to
	 * the first age set. Multiple values are split amongst the age sets in
	 * order, with any values not matching an age set being ignored.
	 * 
	 * @param ageSets
	 *            The collection of age brackets.
	 * @param key
	 *            The region.race.tag specifier.
	 * @param value
	 *            The value of the tag.
	 */
	private void addTagToAgeSet(
			final SortedMap<Integer, SortedMap<String, SortedMap<String, String>>> ageSets,
			String race, String key, final List<String> value)
	{
		final Iterator<String> iter = value.iterator();
		for (int ageBracket : ageNames.values())
		{
			if (!iter.hasNext())
			{
				break;
			}
			final String tagValue = iter.next();
			SortedMap<String, SortedMap<String, String>> races = ageSets
					.get(Integer.valueOf(ageBracket));
			if (races == null)
			{
				races = new TreeMap<>();
				ageSets.put(ageBracket, races);
			}
			SortedMap<String, String> tags = races.get(race);

			if (tags == null)
			{
				tags = new TreeMap<>();
				races.put(race, tags);
			}

			tags.put(key, tagValue);
		}
	}

	private String appendAgesetInfo(Region region,
			final SortedMap<Integer, SortedMap<String, SortedMap<String, String>>> ageSets,
			final StringBuilder sb)
	{
		Set<Integer> ageIndices = new TreeSet<>();
		ageIndices.addAll(ageSets.keySet());
		ageIndices.addAll(ageNames.values());
		// Iterate through ages, outputing the info
		for (Integer key : ageIndices)
		{
			final SortedMap<String, SortedMap<String, String>> races = ageSets.get(key);
			if (races == null)
			{
				continue;
			}

			sb.append("AGESET:");
			sb.append(ageMap.get(region, key).getLSTformat()).append("\n");

			for (final Map.Entry<String, SortedMap<String, String>> stringSortedMapEntry : races.entrySet())
			{
				if (!"AGESET".equals(stringSortedMapEntry.getKey()))
				{
					final SortedMap<String, String> tags = stringSortedMapEntry.getValue();

					for (final Map.Entry<String, String> stringStringEntry : tags.entrySet())
					{
						sb.append("RACENAME:").append(stringSortedMapEntry.getKey()).append("\t\t");
						sb.append(stringStringEntry.getKey()).append(':').append(stringStringEntry.getValue()).append("\n");
					}
				}
			}

			sb.append("\n");
		}

		return sb.toString();
	}

	private void generateAge(final int ageCategory, final boolean useClassOnly, final PlayerCharacter pc)
	{
		// Can't find a base age for the category,
		// then there's nothing to do
		final String age = getTokenNumberInMaps("BASEAGE", ageCategory, pc
			.getDisplay().getRegionString(), pc.getRace().getKeyName().trim());

		if (age == null)
		{
			return;
		}

		// First check for class age modification information
		final int baseAge = Integer.parseInt(age);
		int ageAdd = -1;

		String aClass = getTokenNumberInMaps("CLASS", ageCategory, pc
			.getDisplay().getRegionString(), pc.getRace().getKeyName().trim());

		if (aClass != null && !aClass.equals("0"))
		{
			// aClass looks like:
			// Barbarian,Rogue,Sorcerer[BASEAGEADD:3d6]|Bard,Fighter,Paladin,Ranger[BASEAGEADD:1d6]
			// So first, get the BASEAGEADD
			final StringTokenizer aTok = new StringTokenizer(aClass, "|");

			while (aTok.hasMoreTokens())
			{
				// String looks like:
				// Barbarian,Rogue,Sorcerer[BASEAGEADD:3d6]
				String aString = aTok.nextToken();

				final int start = aString.indexOf("[");
				final int end = aString.indexOf("]");

				// should be BASEAGEADD:xdy
				String dieString = aString.substring(start + 1, end);

				if (dieString.startsWith("BASEAGEADD:"))
				{
					dieString = dieString.substring(11);
				}

				// Remove the dieString
				aString = aString.substring(0, start);

				final StringTokenizer bTok = new StringTokenizer(aString, ",");

				while (bTok.hasMoreTokens() && (ageAdd < 0))
				{
					final String tClass = bTok.nextToken();

					if (pc.getClassKeyed(tClass) != null)
					{
						ageAdd = RollingMethods.roll(dieString);
					}
				}
			}
		}

		// If there was no class age modification,
		// then generate a number based on the .LST
		if ((ageAdd < 0) && !useClassOnly)
		{
			aClass = getTokenNumberInMaps("AGEDIEROLL", ageCategory, pc
				.getDisplay().getRegionString(), pc.getRace().getKeyName().trim());

			if (aClass != null)
			{
				ageAdd = RollingMethods.roll(aClass);
			}
		}

		if ((ageAdd >= 0) && (baseAge > 0))
		{
			final String maxage = getTokenNumberInMaps("MAXAGE", ageCategory, pc
				.getDisplay().getRegionString(), pc.getRace().getKeyName().trim());
			if (maxage != null)
			{
				final int maxAge = Integer.parseInt(maxage);
				if (baseAge + ageAdd > maxAge)
				{
					ageAdd = maxAge-baseAge;
				}
			}
			pc.setAge(baseAge + ageAdd);
		}
	}

	private String generateBioValue(final String addKey, final PlayerCharacter pc)
	{
		final String line = getTokenNumberInMaps(addKey, 0, pc.getDisplay().getRegionString(), pc
			.getRace().getKeyName().trim());
		final String rv;

		if (line != null && line.length() > 0)
		{
			final StringTokenizer aTok = new StringTokenizer(line, "|");
			final List<String> aList = new ArrayList<>();

			while (aTok.hasMoreTokens())
			{
				aList.add(aTok.nextToken());
			}

			final int roll = RollingMethods.roll(1, aList.size()) - 1; // needs to be 0-offset
			rv = aList.get(roll);
		}
		else
		{
			rv = "";
		}

		return rv;
	}

	private void generateHeightWeight(final PlayerCharacter pc)
	{
		int baseHeight = 0;
		int baseWeight = 0;
		int htAdd = 0;
		int wtAdd = 0;
		String totalWeight = null;
		final String htwt = getTokenNumberInMaps("SEX", 0, pc.getDisplay().getRegionString(), pc
			.getRace().getKeyName().trim());

		if (htwt == null)
		{
			return;
		}

		final StringTokenizer genderTok = new StringTokenizer(htwt, "[]", false);

		while (genderTok.hasMoreTokens())
		{
			if (genderTok.nextToken().equals(pc.getDisplay().getGenderObject().toString()))
			{
				final String htWtLine = genderTok.nextToken();
				final StringTokenizer htwtTok = new StringTokenizer(htWtLine, "|", false);

				while (htwtTok.hasMoreTokens())
				{
					final String tag = htwtTok.nextToken();

					if (tag.startsWith("BASEHT:"))
					{
						baseHeight = Integer.parseInt(tag.substring(7));
					}
					else if (tag.startsWith("BASEWT:"))
					{
						baseWeight = Integer.parseInt(tag.substring(7));
					}
					else if (tag.startsWith("HTDIEROLL:"))
					{
						htAdd = RollingMethods.roll(tag.substring(10));
					}
					else if (tag.startsWith("WTDIEROLL:"))
					{
						wtAdd = RollingMethods.roll(tag.substring(10));
					}
					else if (tag.startsWith("TOTALWT:"))
					{
						totalWeight = tag.substring(8);
					}
				}

				if ((baseHeight != 0) && (htAdd != 0))
				{
					pc.setHeight(baseHeight + htAdd);
				}

				if ((totalWeight != null) && (baseWeight != 0) && (wtAdd != 0))
				{
					totalWeight = replaceString(totalWeight, "HTDIEROLL", htAdd);
					totalWeight = replaceString(totalWeight, "BASEWT", baseWeight);
					totalWeight = replaceString(totalWeight, "WTDIEROLL", wtAdd);
					pc.setWeight(pc.getVariableValue(totalWeight, "").intValue());
				}

				break;
			}
			genderTok.nextToken(); // burn next token
		}
	}

	private List<String> mapFind(
			final TripleKeyMapToList<Region, String, String, String> argMap,
			final String argRegionName, final String argRaceName,
			final String addKey, final String altRaceName)
	{
		// First check for region.racename.key
		Region region = Region.getConstant(argRegionName);
		List<String> r = argMap.getListFor(region, argRaceName, addKey);
		if (r != null && !r.isEmpty())
		{
			return r;
		}
		//
		// If not found, try the race name without any parenthesis
		//
		final int altRaceLength = altRaceName.length();

		if (altRaceLength != 0)
		{
			r = argMap.getListFor(region, altRaceName, addKey);
			
			if (r != null)
			{
				return r;
			}
		}
		//
		// If still not found, try the same two searches again without a region
		//
		if (!argRegionName.equals(Constants.NONE))
		{
			region = Region.getConstant("None");
			r = argMap.getListFor(region, argRaceName, addKey);

			if (r != null)
			{
				return r;
			}
			if (altRaceLength != 0)
			{
				r = argMap.getListFor(region, altRaceName, addKey);
			}
		}
		return r;
	}

	public AgeSet addToAgeMap(String regionName, AgeSet ageSet, URI sourceURI)
	{
		AgeSet old =
				ageMap.get(Region.getConstant(regionName), ageSet.getIndex());
		if (old != null)
		{
			if (ageSet.hasBonuses() || !ageSet.getKits().isEmpty() || !ageSet.getName().equals(old.getName()))
			{
				Logging.errorPrint("Found second (non-identical) AGESET "
					+ "in Bio Settings " + sourceURI + " for Region: "
					+ regionName + " Index: " + ageSet.getIndex()
					+ " using the existing " + old.getLSTformat());
			}
			return old;
		}
		ageMap.put(Region.getConstant(regionName), ageSet.getIndex(), ageSet);
		return ageSet;
	}

	public Integer addToNameMap(AgeSet ageSet)
	{
		return ageNames.put(ageSet.getName(), ageSet.getIndex());
	}

	public Set<String> getAgeCategories()
	{
		Set<String> set = new TreeSet<>();
		for (Object o : ageNames.keySet())
		{
			set.add(o.toString());
		}
		return set;
	}

	public Map<Integer, AgeSet> getAgeSets(String regionName)
	{
		return new TreeMap<>(ageMap.getMapFor(Region.getConstant(regionName)));
	}
}
