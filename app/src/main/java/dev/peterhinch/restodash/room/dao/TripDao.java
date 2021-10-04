package dev.peterhinch.restodash.room.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import dev.peterhinch.restodash.room.entities.Trip;

@Dao
public interface TripDao {
    // Create
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrips(Trip... trips);

    // Update
    @Update
    void updateTrips(Trip... trips);

    // Delete
    @Delete
    void deleteTrips(Trip... trips);

    // Delete All
    @Query("DELETE FROM trip")
    void clearTable();

    // Read All
    @Query("SELECT * FROM trip")
    List<Trip> getAllTrips();

    // Read one by ID
    @Query("SELECT * FROM trip WHERE id = :tripId")
    Trip getTripById(int tripId);

    // Find trips by ...

}
