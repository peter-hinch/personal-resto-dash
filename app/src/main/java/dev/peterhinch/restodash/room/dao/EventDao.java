package dev.peterhinch.restodash.room.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import dev.peterhinch.restodash.room.entities.Event;

@Dao
public interface EventDao {
    // Create
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEvents(Event... events);

    // Update
    @Update
    void updateEvents(Event... events);

    // Delete
    @Delete
    void deleteEvents(Event... events);

    // Delete All
    @Query("DELETE FROM event")
    void clearTable();

    // Read All
    @Query("SELECT * FROM event")
    List<Event> getAllEvents();

    // Read one by ID
    @Query("SELECT * FROM event WHERE id = :eventId")
    Event getEventById(int eventId);

    // Find events by ...

}
