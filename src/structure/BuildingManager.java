package structure;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Multimap;

import bwapi.Game;
import bwapi.Pair;
import bwapi.Player;
import bwapi.Position;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import economy.Base;
import economy.ResourceManager;
import economy.Resources;
import economy.SupplyManager;
import economy.WorkerManager;
import idmap.MapUnitID;
import math.MapMath;

/**
 * Building Manager, on each call
 *   - Check if struct in buildOrder needs to be built
 *   - If not, then check if tech in techTree needs to be built
 *   - If not, then check if reached end of buildOrder, then check if supply needs to be built
 *   - If not, then build a unit
 */
public class BuildingManager {

	static int SUPPLY_VALUE_SD = 16;
	static int SUPPLY_VALUE_CC = 20;

	static UnitType SD = UnitType.Terran_Supply_Depot;
	static UnitType CC = UnitType.Terran_Command_Center;
	
	static UnitType Refinery = UnitType.Terran_Refinery;
	static UnitType Academy = UnitType.Terran_Academy;
	static UnitType Barracks = UnitType.Terran_Barracks;
	static UnitType Factory = UnitType.Terran_Factory;
	
	static UnitType SCV = UnitType.Terran_SCV;
	static UnitType Marine = UnitType.Terran_Marine;
	static UnitType Medic = UnitType.Terran_Medic;
	static UnitType TankTank = UnitType.Terran_Siege_Tank_Tank_Mode;
	
	public static boolean buildStruct( Game game, Player self, 
			ArrayList<BaseLocation> bBasePos,
			int mineralSetup,
			Multimap<UnitType, Integer> bArmyMap,
			Multimap<String, Integer> bRolesMap,
			Multimap<UnitType, Integer> bStructMap,
			Resources bResources,
			UnitType struct,
			List<Pair<TilePosition,TilePosition>> noBuildZones
			) {
		// check if enough resources
		if ( !bResources.checkIfEnoughMinsAndGas(struct.mineralPrice(), struct.gasPrice()) ) {
			return false;
		}
		
		// if building type is SD
		if ( struct == SD ) {
			// first SD, use specific location
			if ( MapUnitID.getStructCount(game, bArmyMap, bStructMap, struct) == 0 ) {
				TilePosition pos = BuildingPlacement.getBuildPositionSD(game, bArmyMap, bRolesMap, bStructMap, 
						bBasePos, mineralSetup, noBuildZones);
				// if issue build command passes, update resource manager and exit
				if ( WorkerManager.issueBuildAtLocation(game, bArmyMap, bRolesMap, pos, SD) ) {
					// update bResources
					bResources.addMinAndGas( struct.mineralPrice(), struct.gasPrice() );
					return true;
				}
			}
			else {
				bResources.addMinAndGas( struct.mineralPrice(), struct.gasPrice() );
				return WorkerManager.issueBuild(game, self, bArmyMap, bRolesMap, bStructMap, struct, noBuildZones);				
			}
		}
		// if building type is Barracks
		if ( struct == Barracks ) {
			TilePosition pos = MapMath.findPosFirstBarracks(game, bBasePos.get(0), mineralSetup);
			if ( WorkerManager.issueBuildAtLocation(game, bArmyMap, bRolesMap, pos, Barracks) ) {
				bResources.addMinAndGas( struct.mineralPrice(), struct.gasPrice() );

				return true;
			}
		}
		
		// if building type is an addon
		if ( struct == UnitType.Terran_Machine_Shop ) {
			for ( Integer factoryID : bStructMap.get(UnitType.Terran_Factory ) ) {
				Unit factory = game.getUnit(factoryID);
				if ( factory.canBuildAddon() && factory.isCompleted() ) {
					return factory.buildAddon( struct );
				}
			}
		}
		
		// if building type is a CC
		if ( struct == CC ) {
			return buildCC( game, self, bArmyMap, bRolesMap, bStructMap, bBasePos );
		}
		
		// general case
		if ( bResources.checkIfEnoughMinsAndGas(struct.mineralPrice(), struct.gasPrice() )) {
			bResources.addMinAndGas( struct.mineralPrice(), struct.gasPrice() );
			return WorkerManager.issueBuild(game, self, bArmyMap, bRolesMap, bStructMap, struct, noBuildZones);
		}
		return false;
	}
	
	public static int updateBuildStructTime( Game game ) {
		return game.elapsedTime();
	}
			
	// COMMAND CENTER FUNCTIONS
	
	// method to find build locations
	public static void getBuildingPlan(Game game, Player self, Multimap<UnitType, Integer> bArmyMap,
			Multimap<UnitType, Integer> bStructMap, List<Position> drawStructPos,
			List<String> drawStructLabel, int mineralSetup, ArrayList<BaseLocation> bBasePos) {
		drawStructPos.add( (MapMath.findPosFirstSD(game, bBasePos.get(0), mineralSetup)).toPosition());
		drawStructLabel.add("First Supply Depot");
		drawStructPos.add( (MapMath.findPosFirstBarracks(game, bBasePos.get(0), mineralSetup)).toPosition());
		drawStructLabel.add("First Barracks");
	}

	public static boolean buildCC( Game game, Player self, Multimap<UnitType, Integer> bArmyMap,
			Multimap<String, Integer> bRolesMap,
			Multimap<UnitType, Integer> bStructMap,
			ArrayList<BaseLocation> bBasePos ) {
		int numCC = MapUnitID.getStructCount(game, bArmyMap, bStructMap, CC);
		return WorkerManager.issueBuildAtLocation(game, bArmyMap, bRolesMap, bBasePos.get(numCC).getTilePosition(), CC);
	}
	
	// function to build marines
	public static boolean buildMarines(Game game, Player self, 
			Multimap<UnitType, Integer> bArmyMap, 
			Multimap<UnitType, Integer> bStructMap,
			Resources bResources ) {
		//int marineCount = bArmyMap.get(UnitType.Terran_Marine).size();
		boolean needSupply = SupplyManager.needSupplyCheck(bResources);
		int reservedMinerals = bResources.getMinsActual() - bResources.getMinsEffective();
		for ( Integer barracksID : bStructMap.get(UnitType.Terran_Barracks) ) {
			Unit barracks = game.getUnit(barracksID);
			if (barracks.isTraining() == false && (self.minerals()-reservedMinerals) >= 50 && needSupply == false 
					&& barracks.canTrain( UnitType.Terran_Marine )
					//&& marineCount < (16 * MapUnitID.getStructCount(game, bArmyMap, bStructMap, Barracks) ) 
					) {
				barracks.train(UnitType.Terran_Marine);
				return true;
			}
		}
		return false;
	}
	
	// function to build tanks
	public static boolean buildTanks(Game game, Player self, 
			Multimap<UnitType, Integer> bArmyMap, 
			Multimap<UnitType, Integer> bStructMap,
			Resources bResources ) {
		// check if enough resources
		if ( !bResources.checkIfEnoughMinsAndGas(TankTank.mineralPrice(), TankTank.gasPrice() ) ) {
			return false;
		}
		
		// TODO: add supply check
		if ( SupplyManager.needSupplyCheck(bResources) ) {
			return false; // return false if needSupplyCheck returns true
		}

		// if tank count needs to be restricted
		//int tankCount = bArmyMap.get(UnitType.Terran_Siege_Tank_Tank_Mode).size();	
		
		// build tank from factories
		for ( Integer factoryID : bStructMap.get(UnitType.Terran_Factory) ) {
			Unit factory = game.getUnit(factoryID);
			if ( factory.isTraining() == false 
					//&& tankCount < (16 * MapUnitID.getStructCount(game, bArmyMap, bStructMap, Barracks) ) 
					&& factory.canTrain( UnitType.Terran_Siege_Tank_Tank_Mode ) ) {
				factory.train(UnitType.Terran_Siege_Tank_Tank_Mode );
				return true;
			}
		}
		return false;
	}
	
	public static int getNumPlannedStruct(Game game, Multimap<UnitType, Integer> bArmyMap, UnitType struct) {
		int structCount = 0;
		for ( int scvID : bArmyMap.get(UnitType.Terran_SCV ) ) {
			Unit SCV = game.getUnit(scvID);
			if ( SCV.isConstructing() && SCV.getBuildType() == struct && SCV.canAttack()) {
				structCount++;
			}
		}
		return structCount;
	}
	
	public static int getNumConstructingStruct(Game game, Multimap<UnitType, Integer> bArmyMap, UnitType struct) {
		int structCount = 0;
		for ( int scvID : bArmyMap.get(UnitType.Terran_SCV ) ) {
			Unit SCV = game.getUnit(scvID);
			if ( SCV.isConstructing() && SCV.getBuildType() == struct && !SCV.canAttack()) {
				structCount++;
			}
		}
		return structCount;
	}

	public static void updateSupplyManager(Game game, Player self, Multimap<UnitType, Integer> bArmyMap,
			Multimap<UnitType, Integer> bStructMap,
			Multimap<String, Integer> bRolesMap,
			Resources bResources,
			ArrayList<BaseLocation> bBasePos,
			int mineralSetup,
			List<Pair<TilePosition,TilePosition>> noBuildZones) {
		// get number of supply depots
		int numSupply = MapUnitID.getStructCount(game, bArmyMap, bStructMap, SD);

		// if conditions met to build a supply depot
		if ( SupplyManager.needSupplyCheck(bResources) && ( bResources.getMinsEffective() ) >= 100 ) {
			// first supply depot position
			if ( numSupply == 0 ) {
				// find pos of first SD
				TilePosition pos = MapMath.findPosFirstSD(game, bBasePos.get(0), mineralSetup);
				// issue build at TilePosition found
				WorkerManager.issueBuildAtLocation(game, bArmyMap, bRolesMap, pos, SD);
			}
			else {
				// default build alg
				WorkerManager.issueBuild(game, self, bArmyMap, bRolesMap, bStructMap, SD, noBuildZones);
			}
		}
	}
	
	public static boolean isAddOn( UnitType struct ) {
		if ( struct == UnitType.Terran_Machine_Shop || struct == UnitType.Terran_Comsat_Station
				|| struct == UnitType.Terran_Control_Tower ) {
			return true;
		}
		return false;
	}

	public static void updateProductionMode( Game game,
			Multimap<UnitType, Integer> bArmyMap,
			Multimap<UnitType, Integer> bStructMap,
			int[] productionMode ) {
		if ( MapUnitID.getStructCount(game, bArmyMap, bStructMap, Barracks) >= 1) {
			productionMode[0] = 1;
		}
		// tanks
		if ( MapUnitID.getStructCount(game, bArmyMap, bStructMap, Factory) >= 1) {
			productionMode[0] = 2;
		}
	}
	
	public static Unit getNearestBuildingToUnit( Game game, Multimap<UnitType, Integer> bStructMap, Unit unit, UnitType structType ) {
		int distance = -1;
		Unit closestStruct = null;
		for ( int structID : bStructMap.get(structType) ) {
			Unit struct = game.getUnit(structID);
			int structDistance = (int) BWTA.getGroundDistance(unit.getTilePosition(), struct.getTilePosition());
			if ( (distance == -1) || structDistance < distance ) {
				distance = structDistance;
				closestStruct = struct;
			}
		}
		
		return closestStruct;
	}
	 
	// NEW FUNCTIONS BELOW
	public static void buildingManagerWithBuildOrder( Game game, Player self,
			Multimap<UnitType, Integer> bArmyMap, Multimap<String, Integer> bRolesMap,
			Multimap<UnitType, Integer> bStructMap,
			int[] productionMode, Resources bResources,
			
			List<Pair<TilePosition,TilePosition>> noBuildZones,
			
			ArrayList<BaseLocation> bBasePos,
			List<Pair<UnitType,Integer>> buildOrderStruct,
			List<Pair<TechType,Integer>> buildOrderTech,
			
			int mineralSetup,
			int[] timeBuildIssued,
			
			List<Pair<Position,Position>> miningRegionsList,
			
			ArrayList<Base> bBases) {
		
		// check if supply is same as build order
		if ( BuildingOrder.checkIfSupplyMet(bResources, buildOrderStruct.get(0).second ) ) {
			if ( buildStruct(game, self, bBasePos, mineralSetup, bArmyMap, bRolesMap, bStructMap, bResources, 
					buildOrderStruct.get(0).first, noBuildZones) ) {
				BuildingOrder.setNextOrderNeg(buildOrderStruct); // neg flag means SCV is moving to build
				timeBuildIssued[0] = game.elapsedTime();	// get time build command is issued for error checking
			}
		}
				
		// check if supply is same as build order
		else if ( TechManager.checkIfSupplyMet(bResources, buildOrderTech.get(0).second ) ) {
			if ( TechManager.buildTech(game, bStructMap, bResources, buildOrderTech.get(0).first ) ) {
				buildOrderTech.remove(0); // instant so no need to track time for errors
				return;
			}
			return; // did not build, but still need to
		}
		
		// generic case ( buildOrderStruct supply + tech reads 500 )
		
		// else build UNIT
		else {
			if ( buildOrderStruct.get(0).second < 0 ) { // do not build unit if building is queued
				// check if time since set negative is > 20 seconds, then make it positive. 
				// Usually if this is the case, the SCV did not sucessfully build
				if ( game.elapsedTime() - timeBuildIssued[0] > 20) {
					if ( BuildingOrder.isNextOrderNeg(buildOrderStruct) ) {
						BuildingOrder.setNextOrderNeg(buildOrderStruct); // make it neg to pos		
					}
					else { 
						System.out.println("BuildingManager: More than 20 seconds since build command was issued, but "
								+ "buildOrderStruct has a positive supply value");
					}
				}
				return;
			}
;
			// issue build
			buildUnit(game,self,bArmyMap,bStructMap,productionMode, bResources, bBases);		
		}
	}
	
	public static void buildUnit( Game game, Player self, Multimap<UnitType, Integer> bArmyMap,
			Multimap<UnitType, Integer> bStructMap,
			int[] productionMode, Resources bResources,
			List<Base> bBases ) {
		if ( productionMode[0] == 0 ) {
			if ( buildWorkers(game, self, bArmyMap, bStructMap, bResources, bBases) ) {
				return;
			}	
		}
		if ( productionMode[0] == 1 ) {
			if ( buildWorkers(game, self, bArmyMap, bStructMap, bResources, bBases) ) {
				return;
			}
			if ( buildMarines(game, self, bArmyMap, bStructMap, bResources) ) {
				return;
			}
		}
		if ( productionMode[0] == 2 ) {
			if ( buildWorkers(game, self, bArmyMap, bStructMap, bResources, bBases) ) {
				return;
			}
			if ( buildTanks(game, self, bArmyMap, bStructMap, bResources) ) {
				return;
			}
			if ( buildMarines(game, self, bArmyMap, bStructMap, bResources) ) {
				return;
			}
		} 
	}
	
	public static boolean buildWorkers(Game game, Player self, Multimap<UnitType, Integer> bArmyMap, 
			Multimap<UnitType, Integer> bStructMap,
			Resources bResources,
			List<Base> bBases ) {
		// first check if it is possible: resources + supply
		if ( !bResources.enoughResourcesBuildUnit(SCV) ) {
			return false;
		}
		if ( SupplyManager.needSupplyCheck(bResources) ) {
			return false;
		}
		
		for ( Base bBase : bBases ) {
			if ( bBase.getNumMinMiners() < bBase.getMinMinMiners() ) { // not enough min miners
				Unit CC = bBase.getCC();
				if (CC.isTraining()) continue;
				if (CC.train(SCV)) {
					CC.train(SCV);
					return true;	// tell buildingManager that we built a unit this frame
				}
			}
		}
		return false; // did not train SCV
	}
	
}
