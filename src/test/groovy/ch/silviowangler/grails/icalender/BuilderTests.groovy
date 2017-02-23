package ch.silviowangler.grails.icalender

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Method
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Uid
import org.junit.After
import org.junit.Test

import static net.fortuna.ical4j.model.Component.VEVENT
import static net.fortuna.ical4j.model.Parameter.CN
import static net.fortuna.ical4j.model.Property.*
import static net.fortuna.ical4j.model.parameter.CuType.INDIVIDUAL
import static net.fortuna.ical4j.model.parameter.PartStat.NEEDS_ACTION
import static net.fortuna.ical4j.model.parameter.Role.REQ_PARTICIPANT
import static net.fortuna.ical4j.model.parameter.Rsvp.FALSE
import static net.fortuna.ical4j.model.parameter.Rsvp.TRUE
import static net.fortuna.ical4j.model.property.Method.CANCEL
import static net.fortuna.ical4j.model.property.Method.PUBLISH
import static org.junit.Assert.assertEquals
/*
* Copyright 2007-2014 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/**
 * @author Silvio Wangler
 */
@TestMixin(GrailsUnitTestMixin)
class BuilderTests {

    private ICalendarBuilder builder = new ICalendarBuilder()
    private TimeZoneRegistry registry = TimeZoneRegistryFactory.instance.createRegistry()

    @After
    void tearDown() {
        println builder.cal.toString()
    }

    @Test
    void testBuilderWritesCalendar() {
        builder.calender()
        assert builder.cal == null
    }

    @Test
    void testWithOutExplicitOrganizerDeclaration() {

        TimeZone timeZone = registry.getTimeZone("Europe/Zurich")

        builder.calendar {
            events {
                event(start: new Date(), end: new Date(), description: 'Hi all', summary: 'Short info1')
            }
        }
        builder.cal.validate(true)

        builder.cal.getProperty(METHOD) == PUBLISH

        final VEvent event = builder.cal.getComponents(VEVENT)[0]
        assert event.getProperty(ORGANIZER) != null
        assert event.getProperty(METHOD) == PUBLISH
        assert !event.startDate.isUtc()
        assert !event.endDate.isUtc()
        assert event.startDate.timeZone == timeZone
        assert event.endDate.timeZone == timeZone
        assert event.uid
    }

    @Test
    void testEventWithCustomUid() {

        TimeZone timeZone = registry.getTimeZone("Europe/Zurich")
        def uid = UUID.randomUUID().toString()

        builder.calendar {
            events {
                event(start: new Date(), end: new Date(), description: 'Hi all', summary: 'Short info1', uid: uid)
            }
        }
        builder.cal.validate(true)

        final VEvent event = builder.cal.getComponents(VEVENT)[0]
        assert event.getProperty(ORGANIZER) != null
        assert !event.startDate.isUtc()
        assert !event.endDate.isUtc()
        assert event.startDate.timeZone == timeZone
        assert event.endDate.timeZone == timeZone
        assert event.uid.value == uid
    }

    @Test
    void testUtcTime() {

        builder.calendar {
            events {
                event(start: new Date(), end: new Date(), description: 'Hi all', summary: 'Short info1', utc: true)
            }
        }
        builder.cal.validate(true)

        final VEvent event = builder.cal.getComponents(VEVENT)[0]
        assert event.getProperty(ORGANIZER) != null
        assert !event.getProperty(TZID)
        assert event.startDate.isUtc()
        assert event.endDate.isUtc()
        assert !event.startDate.timeZone
        assert !event.endDate.timeZone
    }

    @Test
    void testEventMethod() {

        builder.calendar {
            events {
                event(start: new Date(), end: new Date(), description: 'Hi all', summary: 'Short info1', method: 'CANCEL', uid: '123')
            }
        }
        builder.cal.validate(true)

        assert builder.cal.getProperty(METHOD) == PUBLISH
        final VEvent event = builder.cal.getComponents(VEVENT)[0]
        assert event.getProperty(ORGANIZER) != null
        assert event.getProperty(METHOD) == CANCEL
    }

    @Test
    void testSimpleTwoEvents() {

        final eventDescription1 = 'Events description'
        final eventDescription2 = 'hell yes'

        builder.calendar {
            events {
                event(start: Date.parse('dd.MM.yyyy HH:mm', '31.10.2009 14:00'), end: Date.parse('dd.MM.yyyy HH:mm', '31.10.2009 15:00'), description: eventDescription1, summary: 'Short info1') {
                    organizer(name: 'Silvio Wangler', email: 'silvio.wangler@amail.com')
                }
                event(start: Date.parse('dd.MM.yyyy HH:mm', '01.11.2009 14:00'), end: Date.parse('dd.MM.yyyy HH:mm', '01.11.2009 15:00'), description: eventDescription2, summary: 'Short info2', location: '@home', classification: 'private') {
                    organizer(name: 'Silvio Wangler', email: 'silvio.wangler@mail.com')
                }
            }
        }
        builder.cal.validate(true) // throws an exception if its invalid

        def events = builder.cal.getComponents(VEVENT)

        assert 2 == events.size()

        assertEquals 'wrong summary', 'Short info1', events[0].summary.value
        assertEquals 'wrong description', eventDescription1, events[0].description.value
        assertEquals 'wrong summary', 'Short info2', events[1].summary.value
        assertEquals 'wrong description', eventDescription2, events[1].description.value

        events.each { VEvent event ->
            assert event.getProperty(TZID).value == 'Europe/Zurich'
            assert event.getProperty(ORGANIZER).value ==~ /mailto:silvio\.wangler@[a]{0,1}mail.com/
            assert event.getProperty(ORGANIZER).parameters.size() == 1
            assert event.getProperty(ORGANIZER).parameters.getParameter(CN).value == 'Silvio Wangler'
        }
    }

    @Test
    void testReminder() {
        builder.calendar {
            events {
                event(start: new Date(), end: (new Date()).next(), summary: 'Text') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                }
            }
        }

        builder.cal.validate()

        def events = builder.cal.getComponents(VEVENT)

        assert 1 == events.size()
        VEvent event = events[0]
        assert event.alarms.size() == 1
        assert event.alarms[0].description.value == 'Alarm 123'
        assert event.alarms[0].trigger.duration.days == 0
        assert event.alarms[0].trigger.duration.hours == 0
        assert event.alarms[0].trigger.duration.minutes == 5
        assert event.alarms[0].trigger.duration.seconds == 0
        assert event.getProperty(TZID).value == 'Europe/Zurich'
    }

    @Test
    void testSetDifferentTimeZoneLondon() {
        TimeZone timeZone = registry.getTimeZone('Europe/London')
        builder.calendar {
            events {
                event(start: new Date(), end: (new Date()).next(), summary: 'Text', timezone: 'Europe/London') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                }
            }
        }

        builder.cal.validate()

        def events = builder.cal.getComponents(VEVENT)

        assert 1 == events.size()
        VEvent event = events[0]
        assert event.getProperty(TZID).value == 'Europe/London'
        assert event.startDate.timeZone == timeZone
        assert event.endDate.timeZone == timeZone
    }

    @Test
    void testSetDifferentTimeZoneToronto() {
        builder.calendar {
            events {
                event(start: new Date(), end: (new Date()).next(), summary: 'Text', timezone: 'America/Toronto') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                }
            }
        }

        builder.cal.validate()

        def events = builder.cal.getComponents(VEVENT)

        assert 1 == events.size()
        VEvent event = events[0]
        assert !event.startDate.isUtc()
        assert !event.endDate.isUtc()
        assert event.getProperty(TZID).value == 'America/Toronto'
    }

    @Test
    void testAddCategories() {
        builder.calendar {
            events {
                event(start: new Date(), end: (new Date()).next(), summary: 'Text', categories: 'icehockey, sports') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                }
            }
        }

        builder.cal.validate()

        def events = builder.cal.getComponents(VEVENT)

        assert 1 == events.size()
        VEvent event = events[0]
        assert event.getProperty(CATEGORIES).value == 'icehockey, sports'
    }

    @Test(expected = IllegalArgumentException.class)
    void testSetDifferentTimeZoneUS() {
        builder.calendar {
            events {
                event(start: new Date(), end: (new Date()).next(), summary: 'Text', timezone: 'US-Eastern:20110928T110000') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                }
            }
        }
    }

    @Test
    void testSupportAttendee() {
        builder.calendar {
            events {
                event(start: new Date(), end: ++(new Date()), summary: 'Text') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                    attendees {
                        attendee(email: 'a@b.ch', role: REQ_PARTICIPANT, partstat: NEEDS_ACTION, cutype: INDIVIDUAL, rsvp: TRUE)
                        attendee(email: 'a@b.it', role: REQ_PARTICIPANT, partstat: NEEDS_ACTION, cutype: INDIVIDUAL, rsvp: FALSE)
                    }
                }
            }
        }
        def events = builder.cal.getComponents(VEVENT)

        assert 1 == events.size()
        VEvent event = events[0]
        assert event.getProperties(ATTENDEE).size() == 2

        for (Attendee attendee : event.getProperties(ATTENDEE)) {
            assert attendee.calAddress.toASCIIString() =~ /mailto:a@b\.(ch|it)/
            assert attendee.getParameter('ROLE') == REQ_PARTICIPANT
            assert attendee.getParameter('PARTSTAT') == NEEDS_ACTION
            assert attendee.getParameter('CUTYPE') == INDIVIDUAL
            assert attendee.getParameter('RSVP') != null
        }
    }

    @Test
    void testSupportAllDayEvents() {
        builder.calendar {
            events {
                allDayEvent(date: Date.parse('dd.MM.yyyy HH:mm', '18.12.2015 13:00'), summary: 'Text') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                    attendees {
                        attendee(email: 'a@b.ch', role: REQ_PARTICIPANT, partstat: NEEDS_ACTION, cutype: INDIVIDUAL, rsvp: TRUE)
                        attendee(email: 'a@b.it', role: REQ_PARTICIPANT, partstat: NEEDS_ACTION, cutype: INDIVIDUAL, rsvp: FALSE)
                    }
                }
            }
        }

        VEvent event = builder.cal.getComponents(VEVENT)[0]

        assert event.startDate.toString().contains('20151218')
        assert event.endDate.toString().contains('20151218')
    }

    @Test
    void testSupportAllDayEventsWithTextInput() {
        builder.calendar {
            events {
                allDayEvent(date: '12.04.2013', summary: 'Text') {
                    organizer(name: 'Silvio', email: 'abc@ch.ch')
                    reminder(minutesBefore: 5, description: 'Alarm 123')
                    attendees {
                        attendee(email: 'a@b.ch', role: REQ_PARTICIPANT, partstat: NEEDS_ACTION, cutype: INDIVIDUAL, rsvp: TRUE)
                        attendee(email: 'a@b.it', role: REQ_PARTICIPANT, partstat: NEEDS_ACTION, cutype: INDIVIDUAL, rsvp: FALSE)
                    }
                }
            }
        }

        VEvent event = builder.cal.getComponents(VEVENT)[0]

        assert event.startDate.toString().contains('20130412')
        assert event.endDate.toString().contains('20130412')
    }

    @Test
    void addXPropertiesToTheCalendar() {

        builder.calendar(xproperties: ['X-WR-RELCALID': '1234', 'X-PRIMARY-CALENDAR': 'TRUE']) {
            events {
                event start: new Date() - 2, end: new Date() - 1, description: 'Hi all', summary: 'Short info1'
                event start: new Date() + 1, end: new Date() + 2, description: 'Hi all', summary: 'Short info1'
            }
        }

        assert builder.cal.properties.size() == 6

        assert builder.cal.properties.getProperty('X-WR-RELCALID').value == '1234'
        assert builder.cal.properties.getProperty('X-PRIMARY-CALENDAR').value == 'TRUE'

        assert builder.cal.getComponents(VEVENT).size() == 2
    }

    // issue #19: https://github.com/saw303/grails-ic-alender/issues/19
    @Test
    void supportStatusMethodAndSequence() {
        builder.calendar(method: 'REQUEST') {
            events {
                event start: new Date(), end: new Date(), description: 'Hi all', summary: 'Short info1', sequence: 12, status: 'CANCELLED', uid: '666', method: 'CANCEL'
            }
        }

        assert builder.cal.getProperty(Property.METHOD) == new Method('REQUEST')
        VEvent event = builder.cal.getComponents(VEVENT)[0]

        assert event.status == Status.VEVENT_CANCELLED
        assert event.sequence == new Sequence(12)
        assert event.uid == new Uid('666')
        assert event.getProperty(Property.METHOD) == Method.CANCEL
    }
}
