package ethos.world.objects;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

import ethos.clip.ObjectDef;
import ethos.model.players.Player;
import ethos.model.players.PlayerHandler;

/**
 * 
 * @author Jason MacKeigan
 * @date Dec 18, 2014, 12:14:09 AM
 */
public class GlobalObjects {

	/**
	 * A collection of all existing objects
	 */
	Queue<GlobalObject> objects = new LinkedList<>();

	/**
	 * A collection of all objects to be removed from the game
	 */
	Queue<GlobalObject> remove = new LinkedList<>();

	/**
	 * Adds a new global object to the game world
	 * 
	 * @param object the object being added
	 */
	public void add(GlobalObject object) {
		updateObject(object, object.getObjectId());
		objects.add(object);
	}

	/**
	 * Removes a global object from the world. If the object is present in the game, we find the reference to that object and add it to the remove list.
	 * 
	 * @param id the identification value of the object
	 * @param x the x location of the object
	 * @param y the y location of the object
	 * @param height the height of the object
	 */
	public void remove(int id, int x, int y, int height) {
		Optional<GlobalObject> existing = objects.stream().filter(o -> o.getObjectId() == id && o.getX() == x && o.getY() == y && o.getHeight() == height).findFirst();
		existing.ifPresent(this::remove);
	}

	/**
	 * Attempts to remove any and all objects on a certain height that have the same object id.
	 * 
	 * @param id the id of the object
	 * @param height the height the object must be on to be removed
	 */
	public void remove(int id, int height) {
		objects.stream().filter(o -> o.getObjectId() == id && o.getHeight() == height).forEach(this::remove);
	}

	/**
	 * Removes a global object from the world based on object reference
	 * 
	 * @param object the global object
	 */
	public void remove(GlobalObject object) {
		if (!objects.contains(object)) {
			return;
		}
		updateObject(object, -1);
		remove.add(object);
	}

	public void replace(GlobalObject remove, GlobalObject add) {
		remove(remove);
		add(add);
	}

	/**
	 * Determines if an object exists in the game world
	 * 
	 * @param id the identification value of the object
	 * @param x the x location of the object
	 * @param y the y location of the object
	 * @param height the height location of the object
	 * @return true if the object exists, otherwise false.
	 */
	public boolean exists(int id, int x, int y, int height) {
		return objects.stream().anyMatch(object -> object.getObjectId() == id && object.getX() == x && object.getY() == y && object.getHeight() == height);
	}

	/**
	 * Determines if any object exists in the game world at the specified location
	 * 
	 * @param x the x location of the object
	 * @param y the y location of the object
	 * @param height the height location of the object
	 * @return true if the object exists, otherwise false.
	 */
	public boolean anyExists(int x, int y, int height) {
		return objects.stream().anyMatch(object -> object.getX() == x && object.getY() == y && object.getHeight() == height);
	}

	/**
	 * Determines if an object exists in the game world
	 * 
	 * @param id the identification value of the object
	 * @param x the x location of the object
	 * @param y the y location of the object
	 * @return true if the object exists, otherwise false.
	 */
	public boolean exists(int id, int x, int y) {
		return exists(id, x, y, 0);
	}

	public GlobalObject get(int id, int x, int y, int height) {
		Optional<GlobalObject> obj = objects.stream().filter(object -> object.getObjectId() == id && object.getX() == x && object.getY() == y && object.getHeight() == height)
				.findFirst();
		return obj.orElse(null);

	}

	/**
	 * All global objects have a unique value associated with them that is referred to as ticks remaining. Every six hundred milliseconds each object has their amount of ticks
	 * remaining reduced. Once an object has zero ticks remaining the object is replaced with it's counterpart. If an object has a tick remaining value that is negative, the object
	 * is never removed unless indicated otherwise.
	 */
	public void pulse() {
		if (objects.size() == 0) {
			return;
		}
		Queue<GlobalObject> updated = new LinkedList<>();
		GlobalObject object = null;
		objects.removeAll(remove);
		remove.clear();
		while ((object = objects.poll()) != null) {
			if (object.getTicksRemaining() < 0) {
				updated.add(object);
				continue;
			}
			object.removeTick();
			if (object.getTicksRemaining() == 0) {
				updateObject(object, object.getRestoreId());
			} else {
				updated.add(object);
			}
		}
		objects.addAll(updated);
	}

	/**
	 * Updates a single global object with a new object id in the game world for every player within a region.
	 * 
	 * @param object the new global object
	 * @param objectId the new object id
	 */
	public void updateObject(final GlobalObject object, final int objectId) {
		List<Player> players = PlayerHandler.nonNullStream().filter(Objects::nonNull)
				.filter(player -> player.distanceToPoint(object.getX(), object.getY()) <= 60 && player.heightLevel == object.getHeight()).collect(Collectors.toList());
		players.forEach(player -> player.getPA().object(objectId, object.getX(), object.getY(), object.getFace(), object.getType()));
	}

	/**
	 * Updates all region objects for a specific player
	 * 
	 * @param player the player were updating all objects for
	 */
	public void updateRegionObjects(Player player) {
		objects.stream().filter(Objects::nonNull).filter(object -> player.distanceToPoint(object.getX(), object.getY()) <= 60 && object.getHeight() == player.heightLevel)
				.forEach(object -> player.getPA().object(object.getObjectId(), object.getX(), object.getY(), object.getFace(), object.getType()));
		loadCustomObjects(player);
	}

	/**
	 * Used for spawning objects that cannot be inserted into the file
	 * 
	 * @param player the player
	 */
	private void loadCustomObjects(Player player) {
		player.getFarming().updateObjects();
		for(int i = 0; i < ObjectDef.totalObjects; i++) {
			ObjectDef def = ObjectDef.getObjectDef(i);
			boolean remove = false;
			if (def != null) {
				if (def.name != null) {
					if (def.name.toLowerCase().contains(("door")) || def.name.toLowerCase().contains(("gate"))) {
					remove = true;
					}
				}
				if(remove){
					remove(i,0);
					remove(i,1);
					remove(i,2);
					remove(i,3);
				}
			}

		}
//		if (HolidayController.CHRISTMAS.isActive()) {
//			player.getPA().checkObjectSpawn(0, 3083, 3500, 0, 10);
//			player.getPA().checkObjectSpawn(1516, 2950, 3824, 0, 10); //Benches
//			player.getPA().checkObjectSpawn(1516, 2952, 3822, 1, 10);
//			player.getPA().checkObjectSpawn(1516, 2949, 3822, 1, 10);
//			player.getPA().checkObjectSpawn(1516, 2959, 3704, 0, 10);
//			player.getPA().checkObjectSpawn(1516, 2960, 3701, 2, 10);
//			player.getPA().checkObjectSpawn(19038, 3083, 3499, 0, 10); //Tree
//			player.getPA().checkObjectSpawn(2147, 2957, 3704, 0, 10); //Ladders
//			player.getPA().checkObjectSpawn(2147, 2952, 3821, 0, 10);
//			player.getPA().checkObjectSpawn(3309, 2953, 3821, 0, 10);
//			player.getPA().checkObjectSpawn(-1, 2977, 3634, 0, 10);
//			player.getPA().checkObjectSpawn(-1, 2979, 3642, 0, 10);
//		}
//		if (HolidayController.HALLOWEEN.isActive()) {
//			player.getPA().checkObjectSpawn(298, 3088, 3497, 0, 10);
//			player.getPA().checkObjectSpawn(298, 3085, 3496, 1, 10);
//			player.getPA().checkObjectSpawn(298, 3085, 3493, 1, 10);
//			player.getPA().checkObjectSpawn(2715, 3088, 3494, 1, 10);
//			player.getPA().checkObjectSpawn(0, 3088, 3496, 0, 10);
//			player.getPA().checkObjectSpawn(0, 3089, 3496, 0, 10);
//			player.getPA().checkObjectSpawn(0, 3088, 3495, 0, 10);
//			player.getPA().checkObjectSpawn(0, 3089, 3495, 0, 10);
		    player.getPA().checkObjectSpawn(-1, 3282, 3498, 0, 10);//new home removed objects begining
		    player.getPA().checkObjectSpawn(-1, 3277, 3498, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3282, 3493, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3277, 3493, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3277, 3503, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3285, 3503, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3287, 3500, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3287, 3498, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3284, 3488, 0, 10);//table
		    player.getPA().checkObjectSpawn(-1, 3286, 3504, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3286, 3503, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3284, 3504, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3284, 3503, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3286, 3502, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3282, 3500, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3283, 3500, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3279, 3500, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3277, 3500, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3278, 3497, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3277, 3497, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3278, 3500, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3282, 3497, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3283, 3497, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3282, 3495, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3283, 3495, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3279, 3493, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3278, 3492, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3276, 3493, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3276, 3494, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3278, 3503, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3278, 3504, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3276, 3503, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3276, 3504, 0, 10);//chair
		    player.getPA().checkObjectSpawn(-1, 3286, 3500, 0, 10);//stool
		    player.getPA().checkObjectSpawn(-1, 3286, 3498, 0, 10);//stool
		    player.getPA().checkObjectSpawn(-1, 3286, 3497, 0, 10);//oakchair
		    player.getPA().checkObjectSpawn(-1, 3285, 3493, 0, 10);//staircase
		    player.getPA().checkObjectSpawn(-1, 3286, 3504, 0, 10);//barrel
		    player.getPA().checkObjectSpawn(-1, 3286, 3510, 0, 10);//barrel
		    player.getPA().checkObjectSpawn(-1, 3286, 3508, 0, 10);//barrel
		    player.getPA().checkObjectSpawn(-1, 3276, 3510, 0, 10);//crate
		    player.getPA().checkObjectSpawn(-1, 3276, 3504, 0, 10);//crate
		    player.getPA().checkObjectSpawn(-1, 3275, 3510, 0, 10);//crate
		    player.getPA().checkObjectSpawn(-1, 3275, 3509, 0, 10);//crate
		    player.getPA().checkObjectSpawn(29150, 3285, 3511, 2, 10);//occult
		    player.getPA().checkObjectSpawn(172, 3279, 3510, 4, 10);//crystal chest
		    player.getPA().checkObjectSpawn(12309, 3282, 3510, 4, 10);//glove chest
		    player.getPA().checkObjectSpawn(12355, 3283, 3511, 0, 10);//portal
		    player.getPA().checkObjectSpawn(6943, 3287, 3500, 1, 10);//bankbooth
		    player.getPA().checkObjectSpawn(6943, 3287, 3498, 1, 10);//bankbooth
		    player.getPA().checkObjectSpawn(8720, 3286, 3503, 2, 10);//votestore
		    player.getPA().checkObjectSpawn(3264, 3272, 3490, 2, 10);//wellofgoodwill
		    player.getPA().checkObjectSpawn(29735, 3264, 3502, 2, 10);//basic train cave
		    player.getPA().checkObjectSpawn(19039, 3273, 3497, 1, 10);//crab cave
		    player.getPA().checkObjectSpawn(4483, 1812, 3791, 1, 10);//bank chest
		    player.getPA().checkObjectSpawn(7489, 1818, 3791, 0, 10);//normal ore
		    player.getPA().checkObjectSpawn(7489, 1818, 3794, 0, 10);//normal ore
		    player.getPA().checkObjectSpawn(7489, 1818, 3797, 0, 10);//normal ore
		    player.getPA().checkObjectSpawn(7492, 1823, 3797, 0, 10);//mith ore
		    player.getPA().checkObjectSpawn(7492, 1823, 3794, 0, 10);//mith ore
		    player.getPA().checkObjectSpawn(7492, 1823, 3791, 0, 10);//mith ore
		    player.getPA().checkObjectSpawn(7460, 1827, 3797, 0, 10);//addy ore
		    player.getPA().checkObjectSpawn(7460, 1827, 3794, 0, 10);//addy ore
		    player.getPA().checkObjectSpawn(7460, 1827, 3791, 0, 10);//addy ore
		    player.getPA().checkObjectSpawn(7461, 1831, 3791, 0, 10);//rune ore
		    player.getPA().checkObjectSpawn(7461, 1831, 3794, 0, 10);//rune ore
		    player.getPA().checkObjectSpawn(7461, 1831, 3797, 0, 10);//rune ore
		    player.getPA().checkObjectSpawn(2030, 1812, 3784, 4, 10);//furnace secret skilling zone
		    player.getPA().checkObjectSpawn(114, 1812, 3788, 2, 10);//cooking range
		    player.getPA().checkObjectSpawn(1276, 1818, 3781, 0, 10);//normal tree
		    player.getPA().checkObjectSpawn(1276, 1818, 3778, 0, 10);//normal tree
		    player.getPA().checkObjectSpawn(1276, 1818, 3775, 0, 10);//normal tree
		    player.getPA().checkObjectSpawn(1751, 1822, 3781, 0, 10);//oak tree
		    player.getPA().checkObjectSpawn(1751, 1822, 3778, 0, 10);//oak tree
		    player.getPA().checkObjectSpawn(1751, 1822, 3775, 0, 10);//oak tree
		    player.getPA().checkObjectSpawn(1760, 1813, 3775, 0, 10);//willow tree
		    player.getPA().checkObjectSpawn(1760, 1807, 3775, 0, 10);//willow tree
		    player.getPA().checkObjectSpawn(1759, 1829, 3781, 0, 10);//maple tree
		    player.getPA().checkObjectSpawn(1759, 1829, 3778, 0, 10);//maple tree
		    player.getPA().checkObjectSpawn(1759, 1829, 3775, 0, 10);//maple tree
		    player.getPA().checkObjectSpawn(1753, 1833, 3781, 0, 10);//yew tree
		    player.getPA().checkObjectSpawn(1753, 1833, 3778, 0, 10);//yew tree
		    player.getPA().checkObjectSpawn(1753, 1833, 3775, 0, 10);//yew tree
		    player.getPA().checkObjectSpawn(2097, 1815, 3791, 0, 10);//anvil
		    player.getPA().checkObjectSpawn(2097, 1815, 3793, 0, 10);//anvil
		    player.getPA().checkObjectSpawn(2097, 1815, 3795, 0, 10);//anvil
		    player.getPA().checkObjectSpawn(-1, 1229, 3494, 0, 10);//removed tree at barrel
		    player.getPA().checkObjectSpawn(22826, 1231, 3496, 0, 10);//orb
		    player.getPA().checkObjectSpawn(22826, 1231, 3497, 0, 10);//orb
		    player.getPA().checkObjectSpawn(-1, 3530, 2837, 0, 10);//unknown location bug
		    player.getPA().checkObjectSpawn(23676, 3280, 3495, 1, 10);//teleport portal
		    player.getPA().checkObjectSpawn(4483, 3278, 2784, 2, 10);//bank chest vespula
		    player.getPA().checkObjectSpawn(22826, 3280, 2783, 0, 10);//orb
		    player.getPA().checkObjectSpawn(22826, 3280, 2784, 0, 10);//orb
		    player.getPA().checkObjectSpawn(22826, 3280, 2785, 0, 10);//orb
		    player.getPA().checkObjectSpawn(22826, 3280, 2786, 0, 10);//orb
		    player.getPA().checkObjectSpawn(-1, 3716, 3948, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3713, 3950, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3712, 3948, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3711, 3951, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3707, 3950, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3706, 3953, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3703, 3951, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3703, 3948, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3700, 3946, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3700, 3948, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3704, 3954, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3705, 3955, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3702, 3956, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3701, 3954, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3699, 3952, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3698, 3953, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3699, 3956, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3697, 3949, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3696, 3952, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3696, 3951, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(-1, 3697, 3947, 0, 10);//tress at boss
		    player.getPA().checkObjectSpawn(7475, 3264, 2784, 0, 10);//portal tekton
		    player.getPA().checkObjectSpawn(21578, 3281, 3492, 0, 10);//Donation stairs home
		    player.getPA().checkObjectSpawn(-1, 3038, 5348, 0, 10);//fair ring
		    player.getPA().checkObjectSpawn(-1, 3038, 5348, 8, 10);//fair ring
		    player.getPA().checkObjectSpawn(13291, 3055, 5347, 8, 10);
		    
		    
//		}
	}

	/**
	 * Loads all object information from a simple text file
	 * 
	 * @throws IOException an exception likely to occur from file non-existence or during reading protocol
	 */
	public void loadGlobalObjectFile() throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(new File("./Data/cfg/global_objects.cfg")))) {
			String line = null;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.startsWith("//")) {
					continue;
				}
				String[] data = line.split("\t");
				if (data.length != 6) {
					continue;
				}
				int id, x, y, height, face, type;
				try {
					id = Integer.parseInt(data[0]);
					x = Integer.parseInt(data[1]);
					y = Integer.parseInt(data[2]);
					height = Integer.parseInt(data[3]);
					face = Integer.parseInt(data[4]);
					type = Integer.parseInt(data[5]);
				} catch (NumberFormatException nfe) {
					System.out.println("WARNING: Unable to load object from file."+lineNumber);
					lineNumber++;
					continue;
				}
				add(new GlobalObject(id, x, y, height, face, type, -1));
				lineNumber++;
			}
		}
	}

	/**
	 * This is a convenience method that should only be referenced when testing game content on a private host. This should not be referenced during the active game.
	 * 
	 * @throws IOException
	 */
	public void reloadObjectFile(Player player) throws IOException {
		objects.clear();
		loadGlobalObjectFile();
		updateRegionObjects(player);
	}

	@Override
	public String toString() {
		List<GlobalObject> copy = new ArrayList<>(objects);
		long matches = objects.stream().filter(o -> copy.stream().anyMatch(m -> m.getX() == o.getX() && m.getY() == o.getY())).count();
		StringBuilder sb = new StringBuilder();
		sb.append("GlobalObjects: <size: " + objects.size() + ", same spot: " + matches + "> [");
		sb.append("\n");
		for (GlobalObject object : objects) {
			if (object == null) {
				continue;
			}
			sb.append("\t<id: " + object.getObjectId() + ", x: " + object.getX() + ", y: " + object.getY() + ">\n");
		}
		sb.append("]");
		return sb.toString();
	}

}
