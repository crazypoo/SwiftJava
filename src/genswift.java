//
//  genswift.java
//  SwiftJava
//
//  Created by John Holdsworth on 14/07/2016.
//  Copyright (c) 2016 John Holdsworth. All rights reserved.
//
//  See genswift.sh..
//  List of classes to be generated received on stdin which is the
//  output of a grep on the target jar for the classes of interest.
//
//  The ordering of frameworks can be specified in argv[0] otherwise
//  the ordering is found by processing all files then re-processing
//  after reordering to minimise the number of forward references in
//  the generated code.

import java.io.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.lang.reflect.*;

class genswift {

	static void print( String s ) {
		System.out.println(s);
	}

	static String Unclassed = "Unclassed";
	static String pathToWriteSource = "./";
	static String organisation = "org.genie.";
	static String proxySourcePath = pathToWriteSource+"src/"+organisation.replace('.', '/');
	static String repoBase = "https://github.com/SwiftJava/";

    boolean isUnclassed( Class<?> type ) {
    	return swiftTypeFor(type, false, true).indexOf(Unclassed) != -1;
    }

    boolean excludeFromCodeGeneration( Class<?> clazz ) {
		return !Modifier.isPublic(clazz.getModifiers()) ||
				classPrefix(clazz.getName()).equals("java_util") && clazz.getName().indexOf('$') != -1;
    }

    boolean supportsProxyCallback( Class<?> clazz ) {
    	String clazzName = clazz.getName();
    	return clazz == java.lang.Runnable.class || isAdapter()
    			|| clazz.isInterface() && (clazzName.endsWith("Listener") || clazzName.endsWith("Handler") || clazzName.endsWith("Manager"));
    }

    boolean isAdapter() {
    	return classSuffix.endsWith("Adapter") && clazz != java.awt.dnd.DropTargetAdapter.class; // missing drop()?
    }

    static HashMap<String,Boolean> swiftReserved = new HashMap<String,Boolean>() {
		private static final long serialVersionUID = 1L;

		{
    		put( Float.class.getName(), true );
    		put( Double.class.getName(), true );
    		put( Object.class.getName(), true );
    		put( String.class.getName(), true );
    		put( Comparable.class.getName(), true );
    		put( Error.class.getName(), true );
    		put( SecurityException.class.getName(), true );
    		put( java.util.Set.class.getName(), true );
    		put( java.util.Locale.class.getName(), true );
    		put( java.util.Comparator.class.getName(), true );
    		put( javax.swing.text.TabSet.class.getName(), true );
    	}
    };

    static HashMap<String,Boolean> subclassResponsibilities = new HashMap<String,Boolean>() {
		private static final long serialVersionUID = 1L;

		{
    		put( "public void java.awt.Window.paint(java.awt.Graphics)", true );
    		put( "public void java.awt.Canvas.paint(java.awt.Graphics)", true );
    		put( "public void java.awt.Canvas.update(java.awt.Graphics)", true );
    		put( "public java.awt.Component javax.swing.JTable.prepareRenderer(javax.swing.table.TableCellRenderer,int,int)", true );
    		put( "public void javax.swing.text.PlainDocument.insertString(int,java.lang.String,javax.swing.text.AttributeSet) throws javax.swing.text.BadLocationException", true );
    		put( "public java.awt.Component javax.swing.table.DefaultTableCellRenderer.getTableCellRendererComponent(javax.swing.JTable,java.lang.Object,boolean,boolean,int,int)", true );
    		put( "public boolean javax.swing.table.DefaultTableModel.isCellEditable(int,int)", true );
    		put( "public void javax.swing.JTable.changeSelection(int,int,boolean,boolean)", true );
		}
    };

    static HashMap<String,String> swiftTypes = new HashMap<String,String>() {
		private static final long serialVersionUID = 1L;

		{
    		put( "boolean", "Bool");
    		put( "byte", "Int8");
    		put( "char", "UInt16");
    		put( "short", "Int16");
    		put( "int", "Int");
    		put( "long", "Int64");
    		put( "float", "Float");
    		put( "double", "Double");
    		put( Float.class.getName(), "Float");
    		put( String.class.getName(), "String");
    	}
    };

    static HashMap<String,String> arrayTypes = new HashMap<String,String>() {
		private static final long serialVersionUID = 1L;

		{
    		put( "boolean", "Bool");
    		put( "byte", "Int8");
    		put( "char", "UInt16");
    		put( "short", "Int16");
    		put( "int", "Int32");
    		put( "long", "Int64");
    		put( "float", "Float");
    		put( "double", "Double");
    		put( String.class.getName(), "String");
    	}
    };

    static HashMap<String,String> funcNames = new HashMap<String,String>() {
		private static final long serialVersionUID = 1L;

		{
    		put( "boolean", "Boolean");
    		put( "byte", "Byte");
    		put( "char", "Char");
    		put( "short", "Short");
    		put( "int", "Int");
    		put( "long", "Long");
    		put( "float", "Float");
    		put( "double", "Double");
    		put( "void", "Void");
    	}
    };

    static HashMap<String,String> jvalueFields = new HashMap<String,String>() {
		private static final long serialVersionUID = 1L;

		{
    		put( "boolean", "z");
    		put( "byte", "b");
    		put( "char", "c");
    		put( "short", "s");
    		put( "int", "i");
    		put( "long", "j");
    		put( "float", "f");
    		put( "double", "d");
    		put( "void", "v");
    	}
    };

    static HashMap<String,Integer> frameworkLevels = new HashMap<String,Integer>();

    static boolean forwardReference( String currentFramework, String framework ) {
    	Integer level = frameworkLevels.get( framework );
    	if ( level == null )
    		return true;
    	return level > frameworkLevels.get( currentFramework );
    }

    String frameworkImports = "\nimport java_swift\n";

    HashMap<String,Boolean> referencedFrameworks = new HashMap<String,Boolean>();

    static int filesWritten = 0;
    static int frameworkLevel = 0;
    static int unclassedReferences = 0;

    public static void main( String args[] ) {

    	String knownFrameworkOrder[] = args[0].split("\\|");

        for ( frameworkLevel=0 ; frameworkLevel<knownFrameworkOrder.length ; frameworkLevel++ ) {
    		String framework = classPrefix(knownFrameworkOrder[frameworkLevel].replace('/', '.')+".");
    		frameworkLevels.put( framework, frameworkLevel );
    		knownAdditionalFrameworks.put(framework, true);
    	}

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            ArrayList<String> paths = new ArrayList<String>();

            String pathToClass;
            while ((pathToClass = reader.readLine())!= null) {
            	paths.add(pathToClass);

            	try {
                	genswift generator = new genswift( pathToClass );
                	if ( generator.generate() )
                		generator.save();
                }
                catch ( Exception e ) {
                    e.printStackTrace();
                }
            }

            if ( additionalFrameworks.size() > 1 ) {
            	HashMap<String,Integer> alreadyMoved = new HashMap<String, Integer>();

                for ( int i=0; i<additionalFrameworks.size(); i++ ) {
        			String mightMove = additionalFrameworks.get(i);
        			if ( alreadyMoved.containsKey(mightMove) && alreadyMoved.get(mightMove) > 10 )
        				continue;
            	
        			int mustBeAfter = i;
            		for ( int j=i+1; j<additionalFrameworks.size(); j++ )
						if (references(mightMove, additionalFrameworks.get(j)) >
							references(additionalFrameworks.get(j), mightMove) )
							mustBeAfter = j;
            		if ( mustBeAfter > i ) {
            			print("Moving "+mightMove+"["+i+"] after "+additionalFrameworks.get(mustBeAfter)+"["+mustBeAfter+"]");
            			additionalFrameworks.remove(i);
            			additionalFrameworks.add(mustBeAfter, mightMove);
            			if ( !alreadyMoved.containsKey(mightMove) )
            				alreadyMoved.put(mightMove, 1);
            			else
            				alreadyMoved.put(mightMove, alreadyMoved.get(mightMove)+1);
            			i--;
            		}
            	}

            	frameworkLevel = knownFrameworkOrder.length;
            	for ( int i=0; i<additionalFrameworks.size(); i++ )
            		if ( frameworkLevels.containsKey(additionalFrameworks.get(i)) )
            			frameworkLevels.put( additionalFrameworks.get(i), frameworkLevel++ );

            	for ( String newFramework : additionalFrameworks ) {
            	    StringBuilder pkg = new StringBuilder();
            	    pkg.append( "\nimport PackageDescription\n\nlet package = Package(\n");
            	    pkg.append("    name: \""+newFramework+"\",\n    dependencies: [\n");
            	
            	    if ( crossReferences.containsKey(newFramework) )
            	    	for ( String depend : crossReferences.get(newFramework).keySet() )
            	    		if ( !newFramework.equals(depend) ) {
            	    			if ( forwardReference( newFramework, depend ) )
            	    				pkg.append("//");
            	    			pkg.append("        Package(url: \"" + repoBase + depend + 
            	    					".git\", versions: Version(1,0,0)..<Version(2,0,0)),\n");
            	    		}

            		pkg.append("        ]\n)\n");

            		try { 
            			FileOutputStream out = new FileOutputStream( pathToWriteSource+newFramework+"/Package.swift" );
            			out.write( pkg.toString().getBytes("UTF-8") );
            			out.close();
            		}
            		catch ( IOException e ) {}
            	}

            	int beforeReorder = unclassedReferences;
            	unclassedReferences = 0;

            	for ( int i=0; i<paths.size(); i++ ) {
                	pathToClass = paths.get(i);
                    try {
                    	genswift generator = new genswift( pathToClass );
                    	if ( generator.generate() )
                    		generator.save();
                    }
                    catch ( Exception e ) {
                        e.printStackTrace();
                    }
                }

                print( "Reorder "+beforeReorder+" -> "+unclassedReferences );
            }
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
 
        print("\n"+filesWritten+" files written.");
    }

    static HashMap<String,HashMap<String,Integer>> crossReferences = new HashMap<String,HashMap<String,Integer>>();
    static HashMap<String,Boolean> knownAdditionalFrameworks = new HashMap<String,Boolean>();
    static ArrayList<String> additionalFrameworks = new ArrayList<String>();

    static void crossReference( String from, String to ) {
    	if ( !crossReferences.containsKey( from ) )
    		crossReferences.put( from, new HashMap<String,Integer>() );
    	if ( !crossReferences.get( from ).containsKey( to ) )
    		crossReferences.get( from ).put( to, 0 );
    	crossReferences.get(from).put(to,crossReferences.get(from).get(to)+1);
    }

    static int references( String from, String to ) {
    	if ( !crossReferences.containsKey(from) || !crossReferences.get(from).containsKey(to) )
    		return 0;
    	return crossReferences.get(from).get(to);
    }

    StringBuilder code = new StringBuilder();
    String pathToClass, className, classSuffix, currentFramework, visibility, classCacheVar;
    boolean isInterface, isLost, isListener;
    Class<?> clazz, superclazz;

    genswift( String pathToClass ) {
    	this.pathToClass = pathToClass;
    }

    void save( ) throws IOException {
    	String Sources = pathToWriteSource + currentFramework + "/Sources/";
    	new File( Sources ).mkdirs();

    	String source = Sources + classSuffix + ".swift";
        byte bytes[] = (frameworkImports+code.toString()).getBytes("UTF-8");
        if ( bytes.length == new File( source ).length() && java.util.Arrays.equals( bytes, existing( source )) )
        	return;

        print( "Saving: "+source);
        FileOutputStream out = new FileOutputStream( source );
        out.write( bytes );
        out.close();
        filesWritten++;
    }

    byte [] existing( String source ) throws IOException {
    	File file = new File( source );
    	if ( !file.exists() )
    		return null;
    	byte bytes[] = new byte[ (int) file.length() ];
    	FileInputStream in = new FileInputStream( source );
    	in.read( bytes );
    	in.close();
    	return bytes;
    }

    boolean generate() throws Exception {
    	className = pathToClass.replace('/', '.');
		clazz = Class.forName( className );

        print( "\n"+clazz );

		if ( excludeFromCodeGeneration( clazz ) )
			return false;

        classSuffix = classSuffix( className );
        currentFramework = classPrefix( className );

		if ( !frameworkLevels.containsKey(currentFramework) )
        	frameworkLevels.put( currentFramework, frameworkLevel++ );
		if ( !knownAdditionalFrameworks.containsKey(currentFramework) ) {
			knownAdditionalFrameworks.put(currentFramework, true);
			additionalFrameworks.add(currentFramework);
		}
//		if ( !currentFramework.equals("java_lang") )
//			classTypeFor( java.lang.Object.class, false, false );
		
        visibility = "open ";
		superclazz = clazz.getSuperclass();
		isInterface = clazz.isInterface();
		isListener = isInterface && supportsProxyCallback( clazz );

		code.append( "\n/// "+clazz+" ///\n" );

		isLost = false;
		String derivedFrom = "";

		if (superclazz != null) {
			String sname = classTypeFor(superclazz, false, true);
			isLost = sname.indexOf(Unclassed+"Object") != -1;
			derivedFrom += ": " + sname;
		} else if (!isInterface)
			derivedFrom += ": JNIObject";

		ArrayList<Class<?>> interfacesSoFar = new ArrayList<Class<?>>();
		Class<?> supr = superclazz;
		while( supr != null ) {
			interfacesSoFar.add(supr);
			supr = supr.getSuperclass();
		}

		boolean hasUnclassed = false;
		for (Class<?> intrface : clazz.getInterfaces()) {
			if ( interfacesChangingReturnTypeInSubclass( intrface ) )
				continue;

			if ( redundantConformance( intrface, interfacesSoFar.toArray( new Class<?>[ interfacesSoFar.size() ] ) )
					|| excludeFromCodeGeneration(intrface) )
				continue;
			interfacesSoFar.add( intrface );

			String name = classTypeFor(intrface, false, true);
			boolean isUnclassed = name.indexOf(Unclassed+"Protocol") != -1;
			if ( isUnclassed )
				if ( hasUnclassed )
					continue;
				else
					hasUnclassed = true;

			if (derivedFrom == "")
				derivedFrom += ": ";
			else
				derivedFrom += ", ";

			derivedFrom += classTypeFor(intrface, false, true);
		}

		if (isInterface && derivedFrom == "")
			derivedFrom += ": JavaProtocol";

		code.append("\n"+(isInterface ? "public protocol" : "open class") + " " + classSuffix + derivedFrom + " {\n\n");

		if ( !isInterface ) {
			code.append("    public convenience init?( casting object: "+swiftTypeFor( java.lang.Object.class, false, true )+", _ file: StaticString = #file, _ line: Int = #line ) {\n");
			code.append("        self.init( javaObject: object.javaObject )\n" );
			code.append("        if !object.validDownCast( toJavaClass: \""+className+"\", file, line ) {\n" );
			code.append("            return nil\n        }\n    }\n\n" );
		}

		classCacheVar = classSuffix+"JNIClass";
		if ( !isInterface )
			code.append( "    private static var "+classCacheVar+": jclass?\n\n" );

		HashMap<String,Boolean> fieldsSeen = new HashMap<String,Boolean>();
		findInterfaceMethods( clazz );

		generateFields( fieldsSeen, isInterface, clazz );

		if ( !isInterface )
			for ( Class<?> intrface : interfacesSoFar.toArray( new Class<?>[ interfacesSoFar.size() ] ) )
				generateInterfaceFields( fieldsSeen, intrface );


		generateConstructors( pathToClass, classSuffix, false );

		boolean hasSubclassResponsibility = generateMethods( clazz.getDeclaredMethods(), isInterface, fieldsSeen, classSuffix, false );

		ArrayList<Method> responsibles = new ArrayList<Method>();
		for ( Method method : clazz.getMethods() ) {
			//print( "!!"+method.toString() );
			if ( subclassResponsibilities.containsKey(method.toString()) )
				responsibles.add( method );
		}

		if ( !isInterface )
			addAnyMethodsDeclaredInProtocolsButNotDefined( isInterface, fieldsSeen, classSuffix );

		code.append("}\n");

		if ( isInterface ) {
			String superProtocol = "JNIObjectForward";
			if ( clazz.getInterfaces().length != 0 )
				superProtocol = classTypeFor( clazz.getInterfaces()[0], false, true )+"Forward";
			code.append( "\nopen class "+classSuffix+"Forward: "+superProtocol+", "+classSuffix+" {\n\n" );
			code.append( "    private static var "+classCacheVar+": jclass?\n\n" );

			findInterfaceMethods( clazz );

			fieldsSeen = new HashMap<String,Boolean>();
			generateFields( fieldsSeen, false, clazz );

			generateMethods( clazz.getMethods(), false, fieldsSeen, classSuffix+"Forward", false );

			addAnyMethodsDeclaredInProtocolsButNotDefined( false, fieldsSeen, classSuffix+"Forward" );

			code.append( "}\n\n\n" );
		}

		if ( isInterface && supportsProxyCallback( clazz ) || isAdapter() || !responsibles.isEmpty() )
			generateCallbackBase( fieldsSeen, responsibles.toArray( new Method[ responsibles.size() ] ) );

		return true;
	}

	int idcount = 0;

	void generateFields( HashMap<String,Boolean> fieldsSeen, boolean isInterface, Class<?> clazz ) {
		for (Field field : clazz.getDeclaredFields()) {
			int mods = field.getModifiers();

			print(field.toString());
			code.append( "    /// "+field+"\n\n" );

			String fieldName = safe(field.getName());
			boolean isFinal = Modifier.isFinal(mods);
			boolean isStatic = Modifier.isStatic(mods);
			
			boolean skipField = (fieldOverride( field, superclazz)) && isStatic ||
					 !Modifier.isPublic(mods) && !Modifier.isProtected(mods) || fieldsSeen.containsKey(fieldName) ||
					 fieldName.equals(classSuffix) || interfaceMethods.containsKey(fieldName+"()") ||
					 isStatic && (Modifier.isProtected(mods) ||
						superclazz == javax.swing.undo.AbstractUndoableEdit.class ||
					 	superclazz != null && superclazz.getSuperclass() == javax.swing.undo.AbstractUndoableEdit.class ||
					 	superclazz == javax.swing.plaf.basic.BasicComboBoxRenderer.class ||
					 	superclazz == javax.swing.border.TitledBorder.class);
			if ( skipField )
				continue;

			fieldsSeen.put(fieldName, true);
			Class<?> fieldType = field.getType();
	    	try {
	    		if ( superclazz != null )
	    			fieldType = superclazz.getField(field.getName()).getType();
	    	}
	    	catch ( NoSuchFieldException e ) {
	    	}
	
//	    	if ( fieldType.isInterface() && fieldType.isArray() )
//	    		continue; ////
	
	    	boolean arrayType = crashesCompilerOnLinx(fieldType);
	    	if ( arrayType )
	    		code.append( "    #if !os(Linux)\n");
	
	    	String fieldIDVar = safe(field.getName())+"_FieldID";
	    	if ( ! isInterface )
	    		code.append( "    private static var "+fieldIDVar+": jfieldID?\n\n" );
	
	    	if ( !Modifier.isStatic(mods) )
	    		fieldIDVar = classSuffix+"."+fieldIDVar;
	
			code.append( "    "+(fieldOverride(field,superclazz)&&!isLost?"override ":"")+(isInterface?"":visibility)+
					(Modifier.isStatic(mods) ? "static " : "")+"var "+fieldName+": "+
					swiftTypeFor(fieldType, true, false) );

			if ( isInterface )
				code.append((isStatic ? isFinal ?" { get }" : " { get set }" : "")+"\n");
            else {
		    	String fieldArgs = "fieldName: \""+field.getName()+"\", fieldType: \""+jniEncoding(field.getType())+"\", fieldCache: &"+fieldIDVar+
		    			(Modifier.isStatic(mods)?
		    					", className: \""+pathToClass+"\", classCache: &"+classCacheVar :
		    					", object: javaObject");
		
                code.append( " {\n" );
                code.append( "        get {\n" );
                code.append( "            let value = JNIField.Get"+funcType( fieldType, mods )+"Field( "+fieldArgs+" )\n" );
                code.append( "            return "+decoder( "value", fieldType )+"\n" );
                code.append( "        }\n" );
                if (!isFinal) {
                    code.append("        set(newValue) {\n");
                    code.append("            let value = " + encoder("newValue", fieldType, "nil") + "\n");
                    code.append("            JNIField.Set" + funcType(fieldType, mods) + "Field( " + fieldArgs
                            + ", value: value" + encodeSuffix(fieldType) + " )\n");
                    code.append("        }\n");
                }
                code.append( "    }\n" );
            }

			if ( arrayType )
	    		code.append( "    #endif\n");
			code.append( "\n" );
		}
	}

	void generateConstructors( String pathToClass, String classSuffix, boolean isListenerBase ) {
		HashMap<String,Boolean> constructorSeen = new HashMap<String,Boolean>();

		for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
			int mods = constructor.getModifiers();

			print(constructor.toString());
			code.append( "    /// "+constructor.toString()+"\n\n" );

	    	String namedSignature = argsFor( constructor, true, true );
			if (  !Modifier.isPublic(mods) && !Modifier.isProtected(mods) || constructorSeen.containsKey(namedSignature) ||  ambiguousInitialiser( constructor.toString() ) )
				continue;
			constructorSeen.put( namedSignature, true );

			String methodIDVar = "new_MethodID_"+(++idcount);
			Constructor<?> overridden = constructorOverride(constructor, superclazz);
			boolean canThrow = constructor.getExceptionTypes().length != 0 && constructor.getParameterCount() != 0 &&
					(overridden == null || overridden.getExceptionTypes().length != 0);

			boolean unnamedOverride = overridden != null;
			if (overridden != null)
				if ( argumentNamesDiffer( constructor, overridden ) )
					overridden = null;

	    	boolean arrayType = false;
	    	for ( Parameter param : constructor.getParameters() )
	    		if ( crashesCompilerOnLinx( param.getType() ) )
	    			arrayType = true;
	
	    	if ( arrayType )
	    		code.append( "    #if !os(Linux)\n");

			code.append("    private static var "+methodIDVar+": jmethodID?\n\n" );

			code.append( "    public "+/*(overridden != null && !isLost && clazz != String.class || isListenerBase ? "override " : "")+*/
					"convenience init("+argsFor( constructor, false, true )+")"+(canThrow?" throws":"")+" {\n" );
			code.append( functionHeader( constructor.getParameters(), null, isListenerBase ? 1 : 0 ) );
			
			String signature = jniArgs(constructor, "");
			if ( isListenerBase ) {
				signature = jniArgs(constructor, "J");
				code.append("\n        self.init( javaObject: nil )\n");
				code.append("        __args["+constructor.getParameterCount()+"] = swiftValue()\n\n");
			}

			code.append( "        let __object = JNIMethod.NewObject( className: \""+pathToClass+"\", classCache: &"+
					classSuffix+"."+classSuffix+"JNIClass, methodSig: \""+signature+"V\", methodCache: &"+classSuffix+"."+methodIDVar+
					", args: &__args, locals: "+(constructor.getParameters().length != 0?"&__locals":"nil")+" )\n" );

			if ( canThrow )
				addThrowCode( constructor );

			if ( isListenerBase )
				code.append( "        self.javaObject = __object\n" );
			else
				code.append( "        self.init( javaObject: __object )\n" );
			code.append( "    }\n" );

	    	String unnamedSigature = argsFor( constructor, true, false );
			if ( !constructorSeen.containsKey(unnamedSigature) && constructor.getParameters().length != 0 ) {

				code.append( "\n    public "+/*(unnamedOverride && !isLost && clazz != String.class || isListenerBase ? "override " : "")+*/
						"convenience init("+argsFor( constructor, false, false )+")"+(canThrow?" throws":"")+" {\n" );

				code.append( "        "+(canThrow?"try ":"")+"self.init("+passthroughArguments(constructor,null)+" )\n    }\n" );

				constructorSeen.put( unnamedSigature, true );
			}

			if ( arrayType )
	    		code.append( "    #endif\n");
	    	code.append( "\n" );
		}
	}

	boolean generateMethods( Method methods[], boolean isProtocol, HashMap<String,Boolean> fieldsSeen, String outputClassName, boolean isListenerBase ) {

		HashMap<String,Boolean> methodsSeen = new HashMap<String,Boolean>();
		boolean hasSubclassResponsibility = false;

		for (Method method : methods ) {
			int mods = method.getModifiers();
			boolean isStatic = Modifier.isStatic(mods);
			String methodIdent = method.toString();

			print(method.toString());
			code.append( "    /// "+method+"\n\n" );

			if ( subclassResponsibilities.containsKey(methodIdent) )
				hasSubclassResponsibility = true;

			Method overridden = funcOverride(method, superclazz);
			if ( overridden != null && Modifier.isPrivate(overridden.getModifiers()) )
				overridden = null;

			boolean unnamedOverride = overridden != null;
			if ( argumentNamesDiffer(method, overridden) )
				overridden = null;

			unnamedOverride = overridden != null;

			String methodName = method.getName();
			boolean fieldExists = fieldsSeen.containsKey(safe(methodName)) && method.getParameterCount() == 0;
			boolean skipMethod = overridden != null && !isStatic && !isListenerBase || !Modifier.isPublic(mods) && !Modifier.isProtected(mods)
					|| isInterface && (dontEnforceProtocol(clazz)
							|| awkwardMethodInProtocol(method) || isUnclassed(method.getReturnType()))
					|| methodName.startsWith("lambda$") || fieldExists;

			if ( skipMethod )
				continue;

			String namedSignature = swiftSignatureFor( method, isProtocol, true, true);
			if ( methodsSeen.containsKey(namedSignature) )
				continue;
			methodsSeen.put(namedSignature, true );

			String methodIDVar = methodName+"_MethodID_"+(++idcount), methodIDVarRef = methodIDVar;
			if ( !isStatic )
				methodIDVarRef = outputClassName+"."+methodIDVarRef;


	    	boolean arrayType = crashesCompilerOnLinx( method );
	    	boolean canThrow = method.getExceptionTypes().length != 0;
			String unnamedSignature = swiftSignatureFor( method, isProtocol, true, false);
	    	boolean createsNameless = !methodsSeen.containsKey(unnamedSignature) && !fieldExists &&
					!(isInterface && lostType(method.getReturnType())) && method.getParameterCount() != 0;
	
	    	if ( arrayType )
	    		code.append( "    #if !os(Linux)\n");

			if ( isProtocol && isStatic )
				code.append("    //");

			String methodKey = methodKey(method);
			Method interfaceMethod = interfaceMethods.get(methodKey);
			interfaceMethods.remove(methodKey);

			boolean createBody = !isListenerBase || !isInterface;

			if ( !(isProtocol && argumentsOfProtocolRenamed( clazz )) ) {
				if ( !isProtocol && (!isListenerBase || !isInterface) )
                	code.append("    private static var "+methodIDVar+": jmethodID?\n\n" );

                code.append("    " + (overridden != null && !isLost && !createBody && !(isListenerBase && isProtocol) 
						|| isAdapter() && isListenerBase ? "override " : "")
						+ swiftSignatureFor(method, isProtocol, false, true, interfaceMethod));

				if ( isListenerBase )
					code.append( " /**/" );

                if (isProtocol)
                    code.append("\n");
                else {
                    code.append(" {\n");

                    if ( createBody ) {
                        code.append( functionHeader( method.getParameters(), interfaceMethod, 0 ) );

                        code.append( "        " );
                        if ( notVoid(method.getReturnType()) ) 
                            code.append( "let __return = " );

                    	String methodArgs = 
                    			(Modifier.isStatic(mods)?
                    					"className: \""+pathToClass+"\", classCache: &"+classCacheVar :
                    					"object: javaObject")+
                    			", methodName: \""+methodName+"\", methodSig: \""+jniSignature(method)+"\", methodCache: &"+methodIDVarRef;

                    	code.append( "JNIMethod.Call"+funcType( method.getReturnType(), mods )+"Method( "+methodArgs+
                    			", args: &__args, locals: "+(method.getParameters().length != 0?"&__locals":"nil")+" )\n" );

                    	if ( canThrow )
                    		addThrowCode( method );

                    	if (notVoid(method.getReturnType()))
                    		code.append("        return "+decoder( "__return", method.getReturnType())+"\n");
                    }
                    else if ( createsNameless ) {
                    	String passthrough = "";
                    	for ( Parameter param : method.getParameters() )
                    		passthrough += (passthrough==""?" ":", ")+safe(param.getName());
                    	String extra = "";
                    	if ( notVoid(method.getReturnType()) )
                    		extra += "return ";
                    	if ( canThrow )
                    		extra += "try! ";
                    	code.append("        "+extra+method.getName()+"("+passthrough+" )\n");
                    }

                    code.append("    }\n\n");
                }
			}

			if ( createsNameless ) {

				if ( isProtocol && isStatic )
					code.append("//");

				code.append("    " + (unnamedOverride && !isLost && !createBody || isAdapter() && isListenerBase ? "override " : "")
						+ swiftSignatureFor(method, isProtocol, false, false, interfaceMethod));

				if ( isListenerBase )
					code.append( " /**/" );

				if (isProtocol)
					code.append("\n");
				else {
					code.append(" {\n");
					if ( createBody )
						code.append("        "+(notVoid(method.getReturnType())?"return ":"")+(canThrow?"try ":"")+
								safe(method.getName()) + "("+passthroughArguments(method, interfaceMethod)+" )\n");
					else if ( notVoid(method.getReturnType()) ) {
                    	String passthrough = "";
                    	for ( Parameter param : method.getParameters() )
                    		passthrough += (passthrough==""?" ":", ")+safe(param.getName())+": _"+safe(param.getName());
						code.append("        return "+ (clazz.isInterface() ?
								method.getReturnType().isPrimitive() ? "0" : "nil" :
								"super."+methodName+"("+passthrough+" )")+"\n");
					}
					code.append("    }\n" );
				}
			}

	    	if ( arrayType )
	    		code.append( "    #endif\n");
	    	code.append( "\n" );

			methodsSeen.put( unnamedSignature, true );
		}

		return hasSubclassResponsibility;
    }

	void generateCallbackBase( HashMap<String,Boolean> fieldsSeen, Method responsibles[] ) throws IOException {
		Method methods[] = responsibles.length != 0 ? responsibles : clazz.getMethods();
		ArrayList<Method> methodsCallingBack = new ArrayList<Method>();

		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			if ( skipCallbackMethod( method ) )
				continue;
			if ( crashesCompilerOnLinx( method ) )
				code.append("#if !os(Linux)\n");

			methodsCallingBack.add( method );

			code.append("private typealias " + jniName(method, i) + "_type = @convention(c) "
					+ jniDecl(method, null) + "\n\n");

			code.append("private func " + jniName(method, i) + jniDecl(method, "_ ") + " {\n");
			String passthrough = "";
			for (Parameter param : method.getParameters())
				passthrough += (passthrough == ""?" ":", ") + safe(param.getName())+
				": "+decoder( safe(param.getName()), param.getType() );//+(!p.getType().isPrimitive()?"!":"");
			String call = classSuffix + "Base.swiftObject( jniEnv: __env, javaObject: __this )."
					+ method.getName() + "(" + passthrough + " )";
			if ( method.getExceptionTypes().length != 0 )
				call = "try! " + call;

			Class<?> returnType = method.getReturnType();
			if ( notVoid(returnType) )
				call = "let __return = "+call;
			code.append("    " + call + "\n");
			if ( notVoid(returnType) )
				code.append("    return "+(returnType.isPrimitive() || returnType == java.lang.String.class ?
						encoder("__return", returnType, "nil") + encodeSuffix(returnType) :
						"JNI.api.NewWeakGlobalRef( JNI.env, __return?.javaObject )")+"\n");

			code.append("}\n");
			if ( crashesCompilerOnLinx( method ) )
				code.append("#endif\n");
			code.append("\n");
		}

		code.append("open class " + classSuffix + "Base: "+(isInterface?"JNIObjectProxy, ":"") + classSuffix + " {\n\n");

		if ( !isInterface )
			code.append( "    private static var "+classSuffix+"BaseJNIClass: jclass?\n" );
		
		code.append("    private static var nativesRegistered = false\n\n");

		code.append("    private static func registerNatives() {\n");
		code.append("        if ( !nativesRegistered ) {\n");
		code.append("            var natives = [JNINativeMethod]()\n\n");

		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			if ( skipCallbackMethod( method ) )
				continue;
			if ( crashesCompilerOnLinx( method ) )
				code.append("            #if !os(Linux)\n");

			String jniName = jniName(method, i);
			code.append("            let " + jniName + "_thunk: " + jniName + "_type = " + jniName + "\n");
			code.append("            natives.append( JNINativeMethod( name: strdup(\"__" + method.getName() + "\")"
					+ ", signature: strdup(\"" + jniSignature(method) + "\"), fnPtr: unsafeBitCast( " + jniName
					+ "_thunk, to: UnsafeMutableRawPointer.self ) ) )\n");

			if ( crashesCompilerOnLinx( method ) )
				code.append("            #endif\n");
			code.append("\n");
		}

		String proxyClass = "org/genie/" + currentFramework + "/" + classSuffix + "Proxy";

		code.append("            withUnsafePointer(to: &natives[0]) {\n");
		code.append("                nativesPtr in\n");
		code.append("                let clazz = JNI.FindClass( \"" + proxyClass + "\" )\n");
		code.append("                if JNI.api.RegisterNatives( JNI.env, clazz, nativesPtr, jint(natives.count) ) != jint(JNI_OK) {\n");
		code.append("                    JNI.report( \"Unable to register java natives\" )\n");
		code.append("                }\n");
		code.append("            }\n\n");
		code.append("            nativesRegistered = true\n");
		code.append("        }\n");
		code.append("    }\n\n");

		if ( isInterface ) {
			code.append("    public convenience init() {\n");
			code.append("        self.init( javaObject: nil )\n");
//		else {
//			code.append("        let object = "+classSuffix+"Base.new()\n");
//			code.append("        self.init( javaObject: object?.javaObject )\n");
			code.append("    }\n\n");
		}

		code.append("    public required init( javaObject: jobject! ) {\n");
		code.append("        super.init( javaObject: javaObject )\n");
		code.append("        "+classSuffix+"Base.registerNatives()\n");
		if ( isInterface )
			code.append("        createProxy( javaClassName: \""+proxyClass+"\" )\n");
		else
			code.append("        updateSwiftObject()\n");
		code.append("    }\n\n");
		code.append("    static func swiftObject( jniEnv: UnsafeMutablePointer<JNIEnv?>?, javaObject: jobject? ) -> " + classSuffix + "Base {\n");
		code.append("        return unsafeBitCast( swiftPointer( jniEnv: jniEnv, object: javaObject ), to: " + classSuffix + "Base.self )\n    }\n\n");

		if ( !isInterface ) {
			generateConstructors( proxyClass, classSuffix+"Base", true );
			methods = methodsCallingBack.toArray( new Method[ methodsCallingBack.size() ] );
		}
		//else
		if ( isInterface || clazz == javax.swing.text.PlainDocument.class )
			generateMethods(methodsCallingBack.toArray( new Method[ methodsCallingBack.size() ] ), false, fieldsSeen, classSuffix + "Base", true);

		code.append("}\n");

		generateJavaCallbackStub( methods ); 
	}
	
	void generateJavaCallbackStub( Method methods[] ) throws IOException {
		StringBuilder java = new StringBuilder();
		
		java.append("\n/// "+clazz+" ///\n\n");

		java.append("package "+organisation+currentFramework+";\n\npublic class "+classSuffix+ "Proxy "+
				(isInterface?"implements ":"extends ")+className.replace('$', '.')+" {\n\n");

		java.append("    long swiftObject;\n\n");

		if ( isInterface ) {
			java.append("    "+classSuffix+"Proxy( long swiftObject ) {\n" );
			java.append("        this.swiftObject = swiftObject;\n    }\n\n" );
		}
		else {
			for ( Constructor<?> constructor : clazz.getDeclaredConstructors() ) {
				java.append("    /// "+constructor+"\n\n");
				int mods = constructor.getModifiers();
				if ( !Modifier.isPublic(mods) && !Modifier.isProtected(mods) )
					continue;
				
				String args = longJavaArgs(constructor);
				java.append("    "+classSuffix+"Proxy("+args+(args==""?" ":", ")+"long swiftObject ) {\n" );

				args = "";
				for (Parameter param : constructor.getParameters()) 
					args += (args == ""?" ":", ")+safe(param.getName());
				java.append("        super("+args+" );\n");
				java.append("        this.swiftObject = swiftObject;\n    }\n\n");
			}
		}

		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			java.append("    /// "+method+"\n\n");
			String methodName = safe(method.getName());
			Class<?> returnType = method.getReturnType();
//			if ( skipCallbackMethod( method ) )
//				continue;

			String args = longJavaArgs(method);
			java.append("    public native "+returnType.getName()+" __"+methodName+"("+args+");\n\n");

			boolean notVoid = notVoid(returnType);
			String retrn = notVoid ? "return " : "";
			String assign = notVoid ? returnType.getName() + " __return = " : "";

			String enteredName = "entered_"+methodName+"_"+i;
			if ( !isInterface )
				java.append("    boolean "+enteredName+";\n\n");

			String throwz = "";
			for ( Class<?> type : method.getExceptionTypes() )
				throwz += (throwz==""?" throws ":", ")+type.getName();
			java.append("    public "+returnType.getName()+" "+methodName+"("+args+")"+throwz+" {\n");

			args = "";
			for (Parameter param : method.getParameters())
				args += (args == ""?" ":", ")+safe(param.getName());

			if ( !isInterface ) {
				java.append("        if ( !"+enteredName+" ) {\n");
				java.append("            "+enteredName+" = true;\n");
				java.append("            "+assign+"__"+methodName+"("+args+" );\n");
				java.append("            "+enteredName+" = false;\n");
				if ( notVoid )
					java.append("            return __return;\n");
				java.append("        }\n");
				java.append("        else\n");
				java.append("            "+retrn+"super."+method.getName()+"("+args+" );\n");
			}
			else
				java.append("        "+retrn+"__"+method.getName()+"("+args+" );\n");
			java.append("    }\n\n");
		}

		java.append("}\n");

		String dest = proxySourcePath+currentFramework;
		new File( dest ).mkdirs();
		String javaSource = dest+"/"+classSuffix+"Proxy.java";
		FileOutputStream out = new FileOutputStream( javaSource );
		out.write(java.toString().getBytes("UTF-8") );
		out.close();
		print("Wrote: "+javaSource);
	}

    void generateInterfaceFields( HashMap<String,Boolean> fieldsSeen, Class<?> intrface ) {
		generateFields( fieldsSeen, isInterface, intrface );									
		for ( Class<?> secondLevel : intrface.getInterfaces() )
			generateInterfaceFields( fieldsSeen, secondLevel );									
    }

	String longJavaArgs( Executable executable ) {
		String args = "";
		for (Parameter param : executable.getParameters()) {
			Class<?> type = param.getType();
			String subs = "";
			String javaType = type.getName();
			while ( type.isArray() ) {
				type = type.getComponentType();
				subs += "[]";
				javaType = type.getName()+subs;
			}
			args += (args == ""?" ":", ")+javaType.replace('$', '.')+" "+safe(param.getName());
		}
		return args == "" ? "" : args + " ";
	}

	boolean interfacesChangingReturnTypeInSubclass( Class<?> intrface ) {
		return intrface == java.util.stream.BaseStream.class
			|| intrface == java.util.concurrent.CompletionStage.class
			|| intrface == java.util.SortedSet.class
			|| intrface == java.util.Iterator.class 
		    || intrface == java.util.concurrent.BlockingQueue.class
		    || intrface == java.util.NavigableSet.class
		    || intrface == java.util.concurrent.locks.ReadWriteLock.class
			|| classPrefix(intrface.getName()).equals("java_util") && intrface.getName().endsWith("Map");
	}

    boolean dontEnforceProtocol( Class<?> clazz ) {
    	return clazz == java.lang.Iterable.class;
    }

    boolean argumentsOfProtocolRenamed( Class<?> clazz ) {
    	return false;
    }

    boolean awkwardMethodInProtocol( Method method ) {
    	return false;
    }
   
   boolean ambiguousInitialiser( String signature ) {
		return signature.equals("public java.awt.Dialog(java.awt.Window)")
				|| signature.equals("public java.awt.Window(java.awt.Frame)") //// crashes compiler on Linux
				|| signature.equals("public javax.swing.JDialog(java.awt.Window)")
				|| signature.equals("public javax.swing.JWindow(java.awt.Window)")
				|| signature.equals("public javax.swing.JDialog(java.awt.Window,java.lang.String)");
    }

	boolean redundantConformance(Class<?> prospectiveInterface, Class<?> interfaces[]) {
		boolean prospectiveUnclassed = isUnclassed(prospectiveInterface);
		for (Class<?> intrface : interfaces)
			if ( prospectiveInterface == intrface || prospectiveUnclassed && isUnclassed(intrface) ||
					redundantConformance(prospectiveInterface, intrface.getInterfaces()))
				return true;
		return false;
	}

    boolean skipCallbackMethod( Method method ) {
    	return awkwardMethodInProtocol( method ) || Modifier.isFinal(method.getModifiers())
    			|| !isInterface && !subclassResponsibilities.containsKey(method.toString()) && !isAdapter();
    }

	HashMap<String,Method> interfaceMethods = new HashMap<String,Method>();

    void findInterfaceMethods( Class<?> clazz ) {
    	for ( Class<?> intrface : clazz.getInterfaces() ) {
    		for ( Method method : intrface.getMethods() )
    			interfaceMethods.put( methodKey( method ), method );
    		findInterfaceMethods( intrface );
    	}
    }

    String methodKey( Method method ) {
    	return method.getName()+jniArgs(method, "");
    }

    void addAnyMethodsDeclaredInProtocolsButNotDefined( boolean isProtocol, HashMap<String,Boolean> fieldsSeen, String outputClassName ) {
		java.util.Collection<Method> inProtocolsButNotDeclared = interfaceMethods.values();
		if ( inProtocolsButNotDeclared.size() != 0 ) {
			code.append( "    /// In declared protocol but not defined.. ///\n\n" );
			Method missingMethods[] = inProtocolsButNotDeclared.toArray( new Method[ inProtocolsButNotDeclared.size() ] );
			generateMethods( missingMethods, isProtocol, fieldsSeen, outputClassName, false );
		}
	}

    String functionHeader( Parameter parameters[], Method interfaceMethod, int extra ) {
    	StringBuilder setup = new StringBuilder();
    	setup.append( "        var __args = [jvalue]( repeating: jvalue(), count: "+Math.max(1,parameters.length+extra)+" )\n" );
    	if ( parameters.length != 0 )
    		setup.append( "        var __locals = [jobject]()\n" );
    	for ( int i=0 ; i<parameters.length ; i++ ) {
    		String name = interfaceMethod!=null ? interfaceMethod.getParameters()[i].getName() : parameters[i].getName();
    		setup.append( "        __args["+i+"] = "+encoder( safe(name), parameters[i].getType(), "&__locals" )+"\n" );
    	}
    	return setup.toString();
    }

    void addThrowCode( Executable executable ) {
        code.append( "        if let throwable = JNI.ExceptionCheck() {\n" );
        code.append( "            throw "+swiftTypeFor(executable.getExceptionTypes()[0], false, false)+"( javaObject: throwable )\n        }\n" );
    }

    String funcType( Class<?> type, int mods ) {
    	String typeName = funcNames.get( type.getName() );
    	if ( typeName == null )
    		typeName = "Object";
    	return (Modifier.isStatic(mods)?"Static":"")+typeName;
    }
 
    String jniArgs( Executable executable, String extra ) {
    	String sig = "(";
    	for ( Parameter param : executable.getParameters() )
    		sig += jniEncoding(param.getType());
    	return sig+extra+")";
    }

    String jniSignature( Method method ) {
    	return jniArgs( method, "" )+jniEncoding(method.getReturnType());
    }

    String jniEncoding( Class<?> clazz ) {
		String name = clazz.getName();
		String type = jvalueFields.get(name);
		return type != null ? type.toUpperCase() : clazz.isArray() ? "[" + jniEncoding(clazz.getComponentType()) : "L"+name.replace('.', '/')+";";
    }

    String jniName( Method method, int i ) {
    	return classSuffix+"_"+safe(method.getName())+"_"+i;
    }

	String jniDecl( Method method, String unnamed ) {
		String decl = "";
		for ( Parameter param : method.getParameters() ) 
			decl += ", "+(unnamed==null?"_":unnamed+safe(param.getName()))+": "+jniType(param.getType());
		return "( "+(unnamed==null?"_":unnamed+"__env")+": UnsafeMutablePointer<JNIEnv?>, "+
					(unnamed==null?"_":unnamed+"__this")+": jobject?"+decl+" )"+" -> "+(notVoid(method.getReturnType()) ? jniType(method.getReturnType()) : "()");
	}

	String jniType( Class<?> type ) {
		return type.isPrimitive() ? "j"+type.getName() : "jobject?";
	}

    boolean argumentNamesDiffer( Executable executable, Executable overridden ) {
    	if ( overridden == null )
    		return true; ////
		for (int i = 0; i < executable.getParameterCount(); i++)
			if (!executable.getParameters()[i].getName()
					.equals(overridden.getParameters()[i].getName()))
				return true;
		return false;
    }

    String passthroughArguments( Executable executable, Method interfaceMethod ) {
		String passthrough = "";
		Parameter parameters[] = executable.getParameters();
		for ( int i=0 ; i<parameters.length ; i++ ) {
			String name = safe(interfaceMethod!=null ? interfaceMethod.getParameters()[i].getName() : parameters[i].getName());
			passthrough += (passthrough == "" ? " " : ", ")+name+": _"+name;
		}
		return passthrough;
    }

    boolean crashesCompilerOnLinx( Method method ) {
    	boolean arrayType = crashesCompilerOnLinx( method.getReturnType() );
		for ( Parameter param : method.getParameters() )
			if ( crashesCompilerOnLinx( param.getType() ) )
				arrayType = true;
		return arrayType;
    }

	boolean crashesCompilerOnLinx( Class<?> type ) {
    	return false;//type.isArray() && !type.getComponentType().isPrimitive();
    }

    String encoder( String var, Class<?> type, String locals ) {
    	if ( type == java.lang.Float.class )
    		return "JNIType.encodeFloat( value: "+var+" )";
    	else if ( type.isInterface() )
    		return "jvalue( l: "+var+"?.javaObject )";
    	else if ( type.isArray() && type.getComponentType().isInterface() )
    		var += "?.map { $0.javaObject }";
    	return "JNIType.encode( value: "+var+", locals: "+locals+" )";
    }

    String encodeSuffix( Class<?> type ) {
    	String jvalueField = jvalueFields.get( type.getName() );
    	if ( jvalueField == null )
    		jvalueField = "l";
    	return "."+jvalueField;
    }

    String decoder( String var, Class<?> type ) {
    	if ( type == java.lang.Float.class )
    		return "JNIType.decodeFloat( from: "+var+" )";
    	String swiftType = swiftTypeFor(type, false, false);
    	if ( type.isInterface() )
    		swiftType += "Forward";
    	if ( type.isArray() && type.getComponentType().isInterface() )
    		swiftType = "["+swiftTypeFor(type.getComponentType(), false, false, false, false)+"Forward]";
    	boolean isObjectType = !type.isPrimitive() && type != String.class && !type.isArray();
    	return isObjectType ? var + " != nil ? " + swiftType+"( javaObject: " + var + " ) : nil" :
    		"JNIType.decode( type: "+swiftType+"(), from: " + var + " )";
    }

    String argsFor( Executable e, boolean anon, boolean named ) {
    	return argsFor( e, anon, named, null );
    }

    String argsFor( Executable e, boolean anon, boolean named, Method interfaceMethod ) {
    	String args = "";
    	for ( int i=0 ; i<e.getParameterCount() ; i++ ) {
    		Parameter param = e.getParameters()[i];
    		String name = interfaceMethod != null ? interfaceMethod.getParameters()[i].getName() : param.getName();
    		args += (args == "" ? " " : ", ") + (named?"":"_ _")+
    				(anon?"arg":safe(name)) + ": " + 
					swiftTypeFor( param.getType(), true, anon, true, true );
    	}
    	return args == "" ? "" : args+" ";
    }
 
    boolean notVoid( Class<?> returnType ) {
    	return !returnType.getName().equals("void");
    }

    static HashMap<String,Boolean> swiftKeywords = new HashMap<String,Boolean>() {
		private static final long serialVersionUID = 1L;

		{
    		put( "init", true );
    		put( "self", true );
    		put( "new", true );
    		put( "in", true );
    		put( "is", true );
    		put( "operator", true );
    		put( "subscript", true );
    		put( "where", true );
    		put( "as", true );
    	}
    };

    String safe( String name ) {
	   return (swiftKeywords.containsKey(name)?"_":"")+name.replace('$','_');
    }

    String swiftSignatureFor( Method method, boolean isProtocol, boolean anon, boolean named ) {
        return swiftSignatureFor( method, isProtocol, anon, named, null );
    }
 
    String swiftSignatureFor( Method method, boolean isProtocol, boolean anon, boolean named, Method interfaceMethod ) {
    	String ret = "";
    	if ( method.getExceptionTypes().length != 0 ) {
    		String exceptions = "";
    		for ( Class<?> exception : method.getExceptionTypes() )
    			exceptions += (exceptions==""?"":", ") + exception.getName();
    		ret += " throws /* "+exceptions+" */";

    	}
    	Class<?> returnType = method.getReturnType();
    	if ( notVoid( returnType ) && !anon )
    		ret += " -> " + swiftTypeFor( returnType, true, false );
    	boolean isStatic = Modifier.isStatic(method.getModifiers());
    	return (isProtocol ? "" : visibility)+(isStatic ? "class ": "")+
    			"func "+safe(method.getName())+"("+argsFor(  method, anon, named, interfaceMethod )+")" + ret;
    }
 
    boolean fieldOverride(Field f, Class<?> superclazz) {
		if (superclazz == null)
			return false;
		if (f.getName().equals("serialVersionUID"))
			return true;
    	try {
    		return superclazz.getField(f.getName()) != null;
    	}
    	catch ( NoSuchFieldException e ) {
    		return false;
    	}
    }

    Constructor<?> constructorOverride(Constructor<?> c, Class<?> superclazz) {
		if (superclazz == null)
			return null;
		Class<?> types[] = c.getParameterTypes();
		//print(""+types.length);

		try {
			switch (types.length) {
			case 0:
				return superclazz.getConstructor();
			case 1:
				return superclazz.getConstructor(types[0]);
			case 2:
				return superclazz.getConstructor(types[0], types[1]);
			case 3:
				return superclazz.getConstructor(types[0], types[1], types[2]);
			case 4:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3]);
			case 5:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3], types[4]);
			case 6:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3], types[4], types[5]);
			case 7:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3], types[4], types[5],types[6]);
			case 8:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3], types[4], types[5], types[6],types[7]);
			case 9:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3], types[4], types[5], types[6],types[7], types[8]);
			case 10:
				return superclazz.getConstructor(types[0], types[1], types[2], types[3], types[4], types[5], types[6],types[7], types[8], types[9]);
			default:
				return null;
			}
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	Method funcOverride(Method m, Class<?> superclazz) {
		if (superclazz == null)
			return null;
		String name = m.getName();
		Class<?> types[] = m.getParameterTypes();
		if ( types.length == 0 && name.equals("registerNatives") )
			return null;////
		try {
			switch (types.length) {
			case 0:
				return superclazz.getMethod(name);
			case 1:
				return superclazz.getMethod(name, types[0]);
			case 2:
				return superclazz.getMethod(name, types[0], types[1]);
			case 3:
				return superclazz.getMethod(name, types[0], types[1], types[2]);
			case 4:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3]);
			case 5:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3], types[4]);
			case 6:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3], types[4], types[5]);
			case 7:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3], types[4], types[5],types[6]);
			case 8:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3], types[4], types[5], types[6],types[7]);
			case 9:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3], types[4], types[5], types[6],types[7], types[8]);
			case 10:
				return superclazz.getMethod(name, types[0], types[1], types[2], types[3], types[4], types[5], types[6],types[7], types[8], types[9]);
			default:
				return null;
			}
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

    String swiftTypeFor( Class<?> type, boolean isReturn, boolean anon ) {
    	return swiftTypeFor( type, isReturn, anon, true, false );
    }

    String swiftTypeFor( Class<?> type, boolean isReturn, boolean anon, boolean prefix, boolean isArg ) {
    	String decl = swiftTypes.get(type.getName());
    	if ( decl == null )
    		if(type.isArray()) {
        		String left = "[", right = "]";
      			Class<?> elementType = type.getComponentType();
    			while ( elementType.isArray() ) {
    				elementType = elementType.getComponentType();
    				left += "[";
    				right += "]";
    			}
    			String nativeType = arrayTypes.get( elementType.getName() );
    			if ( nativeType != null )
    				decl = nativeType;
    			else
    				decl = classTypeFor( elementType, anon, false );

        		decl = (isReturn || true ? "" : "inout ") + left + decl + right;
    		}
    		else
    			decl = classTypeFor( type, anon, prefix );

    	return decl + (isReturn && !type.isPrimitive() /*&& !isListener*/ ? isArg && type != java.lang.Float.class ? "?" : "!" : "");
    }

    String classTypeFor( Class<?> type, boolean anon, boolean prefix ) {
    	String typeName = type.getName();
    	String className = classSuffix( typeName );
    	String frameworkPrefix = classPrefix( typeName );
    	
    	crossReference( currentFramework, frameworkPrefix );

    	if ( lostType( type ) || excludeFromCodeGeneration( type ) ) {
    		unclassedReferences++;
    		return (anon?"":"/* "+typeName+" */ ") + Unclassed + (type.isInterface()?"Protocol":"Object");
    	}

    	if ( !frameworkPrefix.equals(currentFramework) && !type.isPrimitive() ) {
    		if ( !referencedFrameworks.containsKey( frameworkPrefix ) ) {
    			frameworkImports += "import "+frameworkPrefix+"\n";
    			referencedFrameworks.put(frameworkPrefix, true);
    		}
    		if ( prefix )
    			className = frameworkPrefix + "." + className;
    	}
    	return className;
    }

    boolean lostType( Class<?> type ) {
    	return forwardReference( currentFramework, classPrefix( type.getName() ) );
    }

    static int prefixLength( String className ) {
        int firstDot = className.indexOf( '.' );
        if ( firstDot == -1 )
        	return -1;
        int secondDot = className.indexOf( '.', firstDot+1 );
        return secondDot == -1 ? firstDot : secondDot;
    }

    static String classPrefix( String className ) {
    	int  prefixLength = prefixLength( className );
    	if ( prefixLength == -1 )
        	return "java_lang";
        return className.substring( 0, prefixLength ).replace( '.', '_' );
    }

    static HashMap<String,String> allocatedSuffies = new HashMap<String,String>();

    static String classSuffix( String className ) {
        int suffixIndex = className.lastIndexOf( '.' )+1;
        String classSuffix = className.substring( suffixIndex ).replace('$', '_');

        if ( swiftReserved.containsKey(className) )
        	classSuffix = "Java" + classSuffix;

        String allocated = allocatedSuffies.get( classSuffix );
        if ( allocated != null && !allocated.equals(className) ) {
        	int  prefixLength = prefixLength( className );
            String other = className.substring( prefixLength+1, suffixIndex );
        	return other.replace('.', '_') + classSuffix;
        }

        if ( allocated == null )
        	allocatedSuffies.put( classSuffix, className );
        return classSuffix;
    }

}
