package classSrc;

import java.math.BigDecimal;
import java.util.*;

public class Course {
    private String name;
    private String UID;
    private String Code;
    private String description = "";
    private String createDate;
    private Curve curve = new FlatCurve();
    private ArrayList<EnrolledStudent> enrolledStudents = new ArrayList<EnrolledStudent>();
    private ArrayList<Assignment> assignments = new ArrayList<Assignment>();

    public Course(String name, String ID) {
        this.name = name;
        this.UID = UUIDGenerator.getUUID();
        this.Code = ID;
        this.createDate = new Date().toString();
    }
    
    public Course(String Name, String Code, String UID, String Description, String createDate, String curvetype, Double curveValue) {
    	this.name = Name;
    	this.Code = Code;
    	this.UID = UID;
    	this.createDate = createDate;
    	this.description = Description;
    	Curve c = null;
    	if(curvetype.equals("Flat")) {
    		c = new FlatCurve(curveValue);
    	}else if(curvetype.equals("Percentage")) {
    		c = new PercentageCurve(curveValue);
    	}
    	this.curve = c;
    }
    
    //returns the overall score for one particular student in this course, based on the credits and weights of all their grades, the curve of each assignment, and the curve of the overall course
    public Double GetOverallScore(EnrolledStudent es, boolean curved) {
    	Double total = 0.0;
    	ArrayList<Grade> grades = es.getGrades();
    	for(int i = 0; i < grades.size(); i += 1) {
    		Double currentGradeContribution = grades.get(i).calculatePercentageScore(curved) * grades.get(i).getAssignment().getWeight();
    		total += currentGradeContribution;
    	}
    	Double adjustedTotal = curved && this.hasCurve() ? curve.ConvertRawToCurved(total + es.getBonus()) : total;
    	BigDecimal bg = new BigDecimal(adjustedTotal);
    	return bg.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }
    
    public StatisticsHolder CalculateOverallCourseStats(boolean curved) {
    	
    	ArrayList<Double> courseGrades = new ArrayList<Double>();
    	
    	for(EnrolledStudent es : enrolledStudents) {
    		courseGrades.add(GetOverallScore(es, curved));
    	}
    	
    	Collections.sort(courseGrades);
    	
    	
    	Double average = 0.0;
    	
    	for(Double courseGrade : courseGrades) {
    		System.out.println(courseGrade);
    		average += courseGrade / Double.valueOf(courseGrades.size());
    	}
    	
    	if(courseGrades.size()==0) {
    		return new StatisticsHolder(0.0, 0.0, 0.0, 0.0);
    	}
    		
    	int medianIndex = courseGrades.size() / 2;
    	Double median = courseGrades.get(medianIndex);
    	Double min = courseGrades.get(0);
    	Double max = courseGrades.get(courseGrades.size() - 1);
    	
    	System.out.println(average + " " + median + " " + min + " " + max);
    	
    	return new StatisticsHolder(average, median, min, max); 	
    }
    
    
    public StatisticsHolder CalculateAssignmentStats(Assignment a, boolean curved) {
    	
    	//first go through the grades of every student and store which ones belong to this assignment
    	ArrayList<Grade> assignmentGrades = new ArrayList<Grade>();
    	for(int i = 0; i < this.enrolledStudents.size(); i += 1) {
    		ArrayList<Grade> currentStudentGrades = enrolledStudents.get(i).getGrades();
    		for(int j = 0; j < currentStudentGrades.size(); j += 1) {
    			if(currentStudentGrades.get(j).getAssignment().getID().equals(a.getID()) ) {
    				assignmentGrades.add(currentStudentGrades.get(j));
    			}
    		}
    	}
    	
    	//return empty stats holder if there are no grades for this assignment (automatically assigns 0.0 to every statistic)
    	if(assignmentGrades.size() < 1) return new StatisticsHolder();
    	
    	Double average = 0.0;
    	
    	for(Grade g : assignmentGrades) {
    		average += g.calculatePercentageScore(curved) / Double.valueOf(assignmentGrades.size());
    	}
    	
    	ArrayList<Double> credits = new ArrayList<Double>();
    	
    	for(Grade g : assignmentGrades) {
    		credits.add( g.calculatePercentageScore(curved));
    	}
    	
    	Collections.sort(credits);
    	int medianIndex = credits.size() / 2;
    	
    	Double median = credits.get(medianIndex);
    	Double min = credits.get(0);
    	Double max = credits.get(credits.size() - 1);
    	
    	return new StatisticsHolder(average, median, min, max);
    }
    
    
    public String getCreateDate() {
		return createDate;
	}
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public String getUID() {
        return UID;
    }
    
    public void setCode(String Code) {
        this.Code = Code;
    }
    
    public String getCode() {
        return Code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Curve getCurve() {
        return curve;
    }

    public void setCurve(Curve curve) {
        this.curve = curve;
    }
    
    public boolean hasCurve() {
    	return this.curve != null;
    }
    
    public ArrayList<Assignment> getAssignments(){
    	return this.assignments;
    }
    
    public void setAssignments(ArrayList<Assignment> ass){
    	this.assignments = ass;
    	//TODO: at this point, overwrite all of the assignment tuples in the Assignment table in the database with whatever is in this.assignments
    	//after a new assignment configuration has been added, we need to make sure that all of the students' grades match up with any previous assignments, that grades for deleted assignments are deleted, and that grades for new assignments are initialized as default to 0
    	SyncGradesWhenAssignmentUpdate();	
    }
    
    private void SyncGradesWhenAssignmentUpdate() {
    	for(int i = 0; i < this.assignments.size(); i++) {
    		Assignment a = this.assignments.get(i);
    		int index = -2;
    		for(EnrolledStudent s : this.enrolledStudents) {
    			if(index == -2) {
    				index = doesGradeAssignmentExist(a,s);
    				if(index == -1) {
        				s.getGrades().add(i, new Grade(a));
        			}else{
        				Collections.swap(s.getGrades(),index,i);
        			}
    			}else if(index == -1) {
    				s.getGrades().add(i, new Grade(a));
    			}else{
    				Collections.swap(s.getGrades(),index,i);
    			}
    		}   		
    	}
    	//delete redundant score 
    	for(EnrolledStudent s : this.enrolledStudents) {
			s.getGrades().retainAll(this.assignments);
		}
    }
    
    //helper function for SyncGradesWhenAssignmentUpdate
    //Helps ensure that all of the enrolledStudents.grades are consistent with the updated assignment configuration
    //determines whether a student's grade is now obsolete because it references an assignment that has been deleted
    private int doesGradeAssignmentExist(Assignment a, EnrolledStudent s) {
    	int gradeAssignmentFound = -1, i = 0;
    	for(Grade g : s.getGrades()) {
    		if(a.getID().equals(g.getAssignment().getID())){
    			gradeAssignmentFound = i;
    			break;
    		}
    		i++;
    	}
		return gradeAssignmentFound;
    }
    
    private void SyncGradesWhenStudentUpdate() {
    	int length = this.assignments.size();
    	for(EnrolledStudent s : this.enrolledStudents) {
    		if(s.getGrades().size()<length) {
    			for(Assignment a: this.assignments)
    				s.getGrades().add(new Grade(a));
    		}
    	}
    }
    
    public ArrayList<EnrolledStudent> getEnrollStudent(){
    	return this.enrolledStudents;
    }
    
    public void setEnrollStudent(ArrayList<EnrolledStudent> stu){
    	this.enrolledStudents = stu;
    	SyncGradesWhenStudentUpdate(); //need to give all new students empty grades for assignments
    }
  
}